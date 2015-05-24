/*
 * Copyright 2015 Ambud Sharma
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.2
 */
package org.veronica.core.storage;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.configuration.Configuration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.veronica.core.structures.VEdge;
import org.veronica.core.structures.VElement;
import org.veronica.core.structures.VSubGraph;
import org.veronica.core.structures.VVertex;

/**
 * 
 * @author ambudsharma
 */
public class LocalFileStorageSink extends VStorageSink {
	
	private static final Logger logger = LogManager.getLogger(LocalFileStorageSink.class);
	private static final String CONF_BASE = "lfss";
	private static final String CONF_STORAGE_DIR = CONF_BASE+".dir";
	private static final String CONF_STORAGE_SSD = CONF_BASE+".ssd";
	
	private String storageDirectoryPathString;
	private File storageDirectory;
	private boolean ssd;

	public LocalFileStorageSink(String sinkName, Configuration storageConfig) {
		super(sinkName, storageConfig);
	}

	@Override
	public void init() throws VStorageFailureException {
		Configuration config = getStorageConfig();
		// get file storage location
		logger.info("Checking configured storage directory");
		storageDirectoryPathString = config.getString(CONF_STORAGE_DIR, "/tmp/veronica");
		logger.info("Storage configured to:"+storageDirectoryPathString);
		// get is underlying disk is a Solid State Drive (this will have implications on how the flush sizes are computed)
		ssd = config.getBoolean(CONF_STORAGE_SSD, false);
		logger.info("Storage is "+(ssd?"SSD":" not SSD"));
		storageDirectory = new File(storageDirectoryPathString);
		
		// perform validations with the storage
		if(!storageDirectory.exists()) {
			throw new VStorageFailureException(this.getClass(), "Storage directory '"+storageDirectory.getAbsolutePath()+"' does not exist", new FileNotFoundException());
		}
		if(!storageDirectory.isDirectory()) {
			throw new VStorageFailureException(this.getClass(), "Storage path '"+storageDirectory.getAbsolutePath()+"' is not a directory", new FileNotFoundException());
		}
		logger.info("Storage directory exists");
		if(!storageDirectory.canRead() || !storageDirectory.canWrite()) {
			throw new VStorageFailureException(this.getClass(), "Need read/write permissions to write to local storage directory:"+storageDirectory.getAbsolutePath());
		}
		logger.info("Found correct permissions on storage directory");
	}

	@Override
	public VSubGraph readIndex(String graphId) throws VStorageFailureException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void writeIndex(VSubGraph graphId) throws VStorageFailureException {
		// TODO Auto-generated method stub
	}

	@Override
	public VSubGraph readGraphBlock(String graphId)
			throws VStorageFailureException {
		logger.info("Read requsted for graphid:"+graphId);
		VSubGraph graph = null;
		File[] files = storageDirectory.listFiles((dir, name)->name.startsWith(graphId));
		if(files.length==0) {
			throw new VStorageFailureException(LocalFileStorageSink.class, "Invalid graphId:"+graphId+" can't read block from disk");
		}
		logger.info("Found:"+files.length+" versions of shard for graphid:"+graphId);
		File latestDataFile = Arrays.asList(files)
				.stream()
				.sorted((o1, o2)->Long.compare(o2.lastModified(), o1.lastModified()))
				.findFirst().get();
		logger.info("Latest shard for graphid:"+graphId+" is "+latestDataFile.getName());
		String flushTime = latestDataFile.getName().split("\\.")[0].split("_")[1];
		DataInputStream stream = null;
		try {
			stream = new DataInputStream(new FileInputStream(latestDataFile));
		} catch (FileNotFoundException e) {
			throw new VStorageFailureException(LocalFileStorageSink.class, "Graph block file doesn't exist for:"+graphId+" file:"+latestDataFile.getPath(), e);
		}
		try{
			String readGraphId = readGraphId(stream);
			byte[] bloomBytes = readGraphBloom(stream);
			List<VVertex> vertices = readVertices(stream);
			graph = new VSubGraph(readGraphId, vertices.size());
			graph.reinit(bloomBytes, vertices);
			graph.getLastFlush().set(Long.parseLong(flushTime));
		}catch(IOException e) {
			throw new VStorageFailureException(LocalFileStorageSink.class, "Failure to read graphId:"+graphId+" file:"+latestDataFile.getPath()+" from disk", e);
		}finally{
			try {
				stream.close();
			} catch (IOException e) {
				throw new VStorageFailureException(LocalFileStorageSink.class, "Failed to close shard file stream", e);
			}
		}
		return graph;
	}

	@Override
	public long writeGraphBlock(VSubGraph graph)
			throws VStorageFailureException {
		long time = System.currentTimeMillis();
		String graphId = graph.getGraphId();
		File graphShardFile = new File(storageDirectory, graphId+"_"+time+".vr");
		DataOutputStream stream = null;
		try {
			stream = new DataOutputStream(new FileOutputStream(graphShardFile));
		} catch (FileNotFoundException e) {
			throw new VStorageFailureException(LocalFileStorageSink.class, "Storage file not found", e);
		}
		try{
			writeElementId(graphId, stream);
			writeGraphBloom(graph, stream);
			writeVertices(graph, stream);
		}catch(IOException e) {
			e.printStackTrace();
		}finally{
			try {
				stream.close();
			} catch (IOException e) {
				throw new VStorageFailureException(LocalFileStorageSink.class, "Failed to close shard file stream", e);
			}
		}
		return time;
	}

	protected void writeVertices(VSubGraph graph, DataOutputStream stream)
			throws IOException {
		List<VVertex> vertices = graph.getShardVertices();
		for (VVertex vertex : vertices) {
			writeElementId(stream, vertex);
			writeElementLabel(stream, vertex);
			writeVertexEdge(stream, vertex);
		}
	}
	
	protected List<VVertex> readVertices(DataInputStream stream) throws IOException {
		List<VVertex> vertices = new ArrayList<VVertex>();
		while(stream.available()>0) {
			String id = readElementId(stream);
			String label = readElementId(stream);
			
		}
		return vertices;
	}

	protected void writeVertexEdge(DataOutputStream stream, VVertex vertex)
			throws IOException {
		stream.writeInt(vertex.getEdges().size());
		for (VEdge edge : vertex.getEdges()) {
			writeElementId(stream, edge);
			writeElementLabel(stream, edge);
			String otherVertex = edge.getOtherVertex(vertex).getId();
			writeElementId(otherVertex, stream);
		}
	}
	
	protected List<VEdge> readVertexEdge(DataInputStream stream) throws IOException {
		int edgeCount = stream.readInt();
		List<VEdge> edges = new ArrayList<VEdge>(edgeCount);
		for(int i=0;i<edgeCount;i++) {
			String edgeId = readElementId(stream);
			String edgeLabel = readElementLabel(stream);
			String otherVertexId = readElementId(stream);
//			VEdge edge = new VEdge();
		}
		return edges;
	}

	protected void writeElementLabel(DataOutputStream stream, VElement element)
			throws IOException {
		if(element.getLabel()==null) {
			stream.writeInt(-1);
		}else{
			stream.writeInt(element.getLabel().length());
			stream.write(element.getLabel().getBytes());
		}
	}
	
	protected String readElementLabel(DataInputStream stream) throws IOException {
		int val = stream.readInt();
		if(val==-1) {
			return null;
		}else{
			byte[] label = new byte[val];
			stream.readFully(label);
			return new String(label);
		}
	}

	protected void writeElementId(DataOutputStream stream,VElement element)
			throws IOException {
		stream.writeInt(element.getId().length());
		stream.write(element.getId().getBytes());
	}
	
	protected String readElementId(DataInputStream stream) throws IOException {
		byte[] vertexId = new byte[stream.readInt()];
		stream.readFully(vertexId);
		return new String(vertexId);
	}

	protected void writeGraphBloom(VSubGraph graph, DataOutputStream stream)
			throws IOException {
		byte[] bloomBytes = graph.getBloomBytes();
		stream.writeInt(bloomBytes.length);
		stream.write(bloomBytes);
	}
	
	protected byte[] readGraphBloom(DataInputStream stream) throws IOException {
		byte[] bloom = new byte[stream.readInt()];
		stream.readFully(bloom);
		return bloom;
	}

	protected void writeElementId(String graphId, DataOutputStream stream)
			throws IOException {
		stream.writeInt(graphId.length());
		stream.write(graphId.getBytes());
	}
	
	protected String readGraphId(DataInputStream stream) throws IOException {
		byte[] id = new byte[stream.readInt()];
		stream.readFully(id);
		return new String(id);
	}
	
}
