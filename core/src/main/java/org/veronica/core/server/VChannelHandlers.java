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
package org.veronica.core.server;

import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VChannelHandlers {

	private static VChannelHandlers self = new VChannelHandlers();
	private ExecutorService es;
	
	private VChannelHandlers() {
		es = Executors.newFixedThreadPool(10);
	}
	
	public static VChannelHandlers getInstance() {
		return self;
	}
	
	public void submitChannel(SocketChannel channel) {
		es.submit(new WorkerTask(channel));
	}
	
}