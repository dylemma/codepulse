/*
 * Code Pulse: A real-time code coverage testing tool. For more information
 * see http://code-pulse.com
 *
 * Copyright (C) 2014 Applied Visions - http://securedecisions.avi.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.secdec.codepulse.tracer.export

import java.io.BufferedOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import com.secdec.codepulse.data.trace._

import net.liftweb.http.OutputStreamResponse

/** Responsible for exporting traces to a portable format. In the current
  * incarnation, it exports to a .pulse file (which is really just a zip file)
  * containing a .version manifest file and a JSON representation of the data.
  *
  * Output is to an OutputStream. It is up to the caller to pipe this to the
  * appropriate destination. A convenience wrapper is provided to provide an
  * `OutputStreamResponse`.
  *
  * @author robertf
  */
object TraceExporter extends JsonHelpers {
	val Version = 1

	/** Export `data` to `export` */
	def exportTo(out: OutputStream, data: TraceData) {
		val bos = new BufferedOutputStream(out)
		val zout = new ZipOutputStream(bos)

		try {
			write(zout, ".version") { writeVersion(_) }
			write(zout, "trace.json") { writeMetadata(_, data.metadata) }
			write(zout, "nodes.json") { writeTreeNodeData(_, data.treeNodeData) }
			write(zout, "method-correlations.json") { writeMethodCorrelations(_, data.treeNodeData) }
			write(zout, "recordings.json") { writeRecordings(_, data.recordings) }
			write(zout, "encounters.json") { writeEncounters(_, data.recordings, data.encounters) }
		} finally {
			zout.finish
			bos.flush
		}
	}

	/** Export `data`, providing an `OutputStreamResponse`. */
	def exportResponse(data: TraceData) = {
		val headers = List(
			"Content-Type" -> "application/zip",
			"Content-Disposition" -> s"""attachment; filename="${data.metadata.name}.pulse"""")

		OutputStreamResponse(exportTo(_, data), -1L, headers, Nil, 200)
	}

	private def write[T](zout: ZipOutputStream, name: String)(f: OutputStream => T) = {
		zout putNextEntry new ZipEntry(name)

		try {
			f(zout)
		} finally zout.closeEntry
	}

	private def writeVersion(out: OutputStream) {
		val writer = new OutputStreamWriter(out)
		try {
			writer write Version
		} finally writer.flush
	}

	private def writeMetadata(out: OutputStream, metadata: TraceMetadataAccess) {
		streamJson(out) { jg =>
			jg.writeStartObject

			jg.writeStringField("name", metadata.name)
			jg.writeNumberField("creationDate", metadata.creationDate)
			for (importDate <- metadata.importDate) jg.writeNumberField("importDate", importDate)

			jg.writeEndObject
		}
	}

	private def writeTreeNodeData(out: OutputStream, treeNodeData: TreeNodeDataAccess) {
		streamJson(out) { jg =>
			jg.writeStartArray

			treeNodeData.foreach { node =>
				jg.writeStartObject

				jg.writeNumberField("id", node.id)
				for (parentId <- node.parentId) jg.writeNumberField("parentId", parentId)
				jg.writeStringField("label", node.label)
				jg.writeStringField("kind", node.kind.label)
				for (size <- node.size) jg.writeNumberField("size", size)

				jg.writeEndObject
			}

			jg.writeEndArray
		}
	}

	private def writeMethodCorrelations(out: OutputStream, treeNodeData: TreeNodeDataAccess) {
		streamJson(out) { jg =>
			jg.writeStartArray

			treeNodeData.foreachMapping { (sig, nodeId) =>
				jg.writeStartObject
				jg.writeStringField("signature", sig)
				jg.writeNumberField("node", nodeId)
				jg.writeEndObject
			}

			jg.writeEndArray
		}
	}

	private def writeRecordings(out: OutputStream, recordings: RecordingMetadataAccess) {
		streamJson(out) { jg =>
			jg.writeStartArray

			for (recording <- recordings.all) {
				jg.writeStartObject
				jg.writeNumberField("id", recording.id)
				jg.writeBooleanField("running", recording.running)
				for (label <- recording.clientLabel) jg.writeStringField("label", label)
				for (color <- recording.clientColor) jg.writeStringField("color", color)
				jg.writeEndObject
			}

			jg.writeEndArray
		}
	}

	private def writeEncounters(out: OutputStream, recordings: RecordingMetadataAccess, encounters: TraceEncounterDataAccess) {
		streamJson(out) { jg =>
			jg.writeStartObject

			jg writeFieldName "all"
			jg.writeStartArray
			encounters.get.foreach(jg.writeNumber)
			jg.writeEndArray

			for (recording <- recordings.all.map(_.id)) {
				jg writeFieldName recording.toString
				jg.writeStartArray
				encounters.get(recording).foreach(jg.writeNumber)
				jg.writeEndArray
			}

			jg.writeEndObject
		}
	}
}