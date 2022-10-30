/*
 * Copyright (c)2022 National Institute of Advanced Industrial Science 
 * and Technology (AIST). All rights reserved.
 */

package org.meow.app;

import java.io.InputStream;
import java.io.DataInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.DataOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.net.Socket;
import java.net.InetSocketAddress;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Hashtable;

/*
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;
*/

public class AppControllerV3 extends Thread
{
    private static final Logger log = LoggerFactory.getLogger(AppControllerV3.class);
    public static final boolean DEBUG = false;
    public static final boolean DEBUG2 = false;
    public static final boolean ResponseWait = true;
    // public static final boolean ResponseWait = false;

    public static final short N_VERSION = 3;
    public static final int PacketLength = RequestV3.PacketLength;
    public static final int HeadLength = RequestV3.HeadLength;
    public static final int GetStatusLength = HeadLength + PacketLength;
    public static final boolean DO_RECV_THREAD = RequestSetV3.DO_RECV_THREAD;
    public static short[] getStatusRequest = null;

    private boolean stopThread = false;
    private short[] statusData = null;
    private short[] statusCode = null;
    private String status = null;
    private int waitResponseId = -1;
    private Object waitResponse = new Object();
    private short[] waitStatus = new short[PacketLength];

    static public short requestId= 0;
    private short[] sendp = null;
    
    private Socket socket = null;
    private OutputStream outStream = null;
    private DataOutputStream outData = null;
    private InputStream inStream = null;
    private DataInputStream inData = null;

    private SocketChannel channel = null;
    private Selector selector;

    private InetSocketAddress iaddr =  null;
    private ByteBuffer rbufHead = null;
    private ByteBuffer rbufBody = null;
    private ByteBuffer sbuf = null;
    private boolean sbuf_used = false;
    private boolean rbuf_used = false;
    // private Hashtable responses = new Hashtable<Integer, ResponseSetV3>();
    // private Hashtable callback = new Hashtable<Integer, RequestSetV3>();
    private ResponseSetV3 nullResponse = new ResponseSetV3();

    private int bufsize = 1400;

    private String addr = null;
    private int port = 0;
    private int sequenceId = -1;

    public AppControllerV3 (String addr, int port)
    {
	this.addr = addr;
	this.port = port;
	rbufHead = ByteBuffer.allocate(HeadLength * 2);
	rbufBody = ByteBuffer.allocate(PacketLength * 2);
	sbuf = ByteBuffer.allocate(bufsize);

	try {
	    iaddr = new InetSocketAddress(addr, port);
	    channel = SocketChannel.open(iaddr);
	    // connect(); 

	    if (DO_RECV_THREAD) {
		channel.configureBlocking(false);
		selector = Selector.open();
		channel.register(selector, SelectionKey.OP_READ);
	    }
	} catch (IOException ex) {
	    log.error("AppControllerV3: connect error: addr={}, port{}, ex={}",
		      addr, port, ""+ex);
	}
    }
	
    static private synchronized short getRequestId() 
    {
	short seq = requestId++;
	return seq;
    }

    public void close()
    {
	if (DEBUG) log.info("AppControllerV3:close(): channel={}", channel);
	if (channel == null)  return;
	try {
	    if (DEBUG) log.info("AppControllerV3:close(): isOpen={}", channel.isOpen());
	    if (channel.isOpen()) {
		// stopThread = true;
		channel.close();
	    }
        } catch (IOException ex) {
	    log.error("AppControllerV3: close ex= {}.", "" + ex);
            // System.err.println("ex = " + ex);
	    // ex.printStackTrace();
	} finally {
	    channel = null;
	}
	return;
    }

    public boolean isOpen() 
    {
	if (DEBUG) log.info("isOpen(): channel={}", channel); 
	if (channel == null) return false; //connect();

	boolean f = channel.isOpen();
	if (DEBUG) log.info("isOpen(): isOpen()={}", f);

	if (f) return true;
	else {
	    try {
		throw new Exception("TEST in isOpen8).");
	    } catch (Exception ex) {
		if (DEBUG) log.info("do_recv: ex = {}.", ex);
		log.error("AppControllerV3: connect trace= {}.", "" + getString(ex));
	    }
	    return false;
	} 
    }

    public synchronized boolean connect()
    {
	try {
	    if (channel != null) channel.close();

	    channel = SocketChannel.open(iaddr);
	    if (DEBUG) log.info("AppControllerV3: connect {}.", "" + channel);
	    return true;
	} catch (Exception ex) {
	    log.error("AppControllerV3: connect ex= {}.", "" + ex);
	    // System.err.println("ex = " + ex);
	    log.error("AppControllerV3: connect trace= {}.", "" + getString(ex));
	    ssleep(3);
	}
	return false;
    }

    public String getString(Exception ex) 
    {
	StackTraceElement[] stacks = ex.getStackTrace();
	String s = "";
	int n = 0;
	for (StackTraceElement element : stacks) {
	    s += "\n\t" + element ;
	    n ++;
	    if (n > 5) break;
	}
	s += "\n\t";
	return s;
    }

    public short[] getGetStatusRequest() {
	if (getStatusRequest == null) {
	    getStatusRequest = new short[GetStatusLength];
	    for (int i = 0; i < GetStatusLength; i++) {
		getStatusRequest[i] = 0;
	    }
	    getStatusRequest[0] = 3;
	    getStatusRequest[1] = 1;
	    getStatusRequest[7] = 3;
	}
	getStatusRequest[2] = (short) getRequestId();
	return getStatusRequest;
    }

    public synchronized ResponseSetV3 sendShort(short[] param) 
    {
	if (! isOpen()) return null;
	if (param == null) return null;

	int length = param.length;
	int bytes = length * 2;
	if (bytes > bufsize) {
	    log.error("Too long control parame={}.", length);
	    return null;
	}

	int requestId = Short.toUnsignedInt(param[2]);
	Integer key = new Integer(requestId);
	ResponseSetV3 res = (ResponseSetV3) ResponseSetV3.responses.get(key);
	if (res == null) {
	    if (DEBUG) log.info("sendShort: add responses key={}", key);
	    res = new ResponseSetV3();
	    ResponseSetV3.responses.put(key, res);
	} 
	res.sparam = param;
	
	// Commands are unified to either setup or disconnect. 
	// They cannot be mixed within one control packet.
	int command = Short.toUnsignedInt(param[7]);

	ByteBuffer buf = ByteBuffer.allocate(bytes);
	for (int i = 0; i < length; i++) {
	    buf.putShort(param[i]);
	    if (DEBUG) log.info("AppControllerV3:sendShort: i={}, v={}",
	    			i, param[i]);
	}
	
	buf.flip();
	if (DEBUG) log.info("AppControllerV3:sendShort: buf={}", length);

	int r = 0;
	try {
	    while (r < bytes) {
		r += channel.write(buf);
	    }
	    if (DEBUG) log.info("AppControllerV3:sendShort: done. length={}", r);
	} catch (IOException ex) {
	    log.error("sendShort ex = {}.", "" + ex);
	    close();
	    return null;
	}
	if (DEBUG)
	    if (res != null) log.info("sendShort: put(key={}, res={})", key, res);

	switch (command) 
	    {
	    case 1: // setup
	    case 2: // teardown
		return readResult(key);
	    case 3: // getStatus
		return readStatus(key);
	    default:
		log.error("sendShort: bad command = {}", command);
		return null;
	    }
    }

    public synchronized ResponseSetV3 readResult(Integer key) 
    {
	ResponseSetV3 res = null;
	if (DO_RECV_THREAD) {
	    res = (ResponseSetV3) ResponseSetV3.responses.get(key);
	    if (res != null && res.checkRecvDone()) {
		if (DEBUG && res != null) log.info("read Response: res={}", 
						   res.toString());
		if (res.checkRecvDone()) {
		    ResponseSetV3.responses.remove(key);
		    return res;
		}
	    }
	
	    do {
		try {
		    if (ResponseWait) break;

		    if (DEBUG2) log.info("read Response: wait. key={}", key);
		    wait();
		    res = (ResponseSetV3) ResponseSetV3.responses.get(key);
		    if (DEBUG2) log.info("read Response: wait. get res={}", res);

		    if (res == null) continue;

		    if (res.checkRecvDone()) {
			ResponseSetV3.responses.remove(key);
			if (DEBUG2) log.info("read Response: checkRecvDone. true");
			break;
		    } else {
			if (DEBUG2) log.info("read Response: checkRecvDone. false");
			// break;
		    }
		} catch (Exception e) {}
	    } while (true);
	    
	    if (DEBUG2) log.info("readShort #0: requestId={}, response={}", 
				 requestId, res.toString());
	    return res;
	} else {
	    do {
		try {
		    if (DEBUG) log.info("read Response #1: recv. key={}", key);
		    if (do_recv() == -1) return null;
		    
		    res = (ResponseSetV3) ResponseSetV3.responses.get(key);
		    if (DEBUG) log.info("readResult: read done. get res={}", res);
		    if (res.checkRecvDone()) {
			ResponseSetV3.responses.remove(key);
			break;
		    }
		} catch (IOException e) {
		    if (DEBUG) log.info("do_recv ex=" + e);
		    isOpen();
		}
	    } while (res == null || (! res.checkRecvDone()));
	
	    if (DEBUG) log.info("readShort #2: requestId={}, res={}", 
				 requestId, res.toString());
	    return res;
	}
    }

    public synchronized ResponseSetV3 readStatus(Integer key) 
    {
	ResponseSetV3 res = null;
	if (DEBUG) log.info("readStatus: DO_RECV_THREAD = {}", DO_RECV_THREAD);
	if (DO_RECV_THREAD) {
	    res = (ResponseSetV3) ResponseSetV3.responses.get(key);
	    if (res == null) {
		if (DEBUG) log.info("readStatus: res = null");
	    } else {
		if (res.checkRecvDone()) {
		    if (DEBUG2  && res != null) log.info("readStatus: res={}", 
							 res.toString());
		    if (res.checkRecvDone()) {
			ResponseSetV3.responses.remove(key);
			return res;
		    }
		}
	    }
	    
	    if (DEBUG) log.info("readStatus: wait. key={}", key);
	    do {
		try {
		    wait();
		    res = (ResponseSetV3) ResponseSetV3.responses.get(key);
		    if (DEBUG) log.info("readStatus: wait. res={}", res);
		    
		    if (res == null) continue;
		    if (res.checkRecvDone()) {
			ResponseSetV3.responses.remove(key);
			break;
		    }
		} catch (Exception e) {}
	    } while (true);
	    
	    if (DEBUG) log.info("readStatus: requestId={}, status={}", 
				 requestId, res.toString());
	    return res;
	} else {
	    do {
		try {
		    do_recv();
		    res = (ResponseSetV3) ResponseSetV3.responses.remove(key);
		    if (DEBUG) log.info("readStatus: read done. get res={}", res);

		    if (res.checkRecvDone()) {
			ResponseSetV3.responses.remove(key);
			break;
		    }
		} catch (IOException e) {
		    if (DEBUG) log.info("do_recv ex=" + e);
		    isOpen();
		}
	    } while (res == null || (! res.checkRecvDone()));
	    if (DEBUG) log.info("readShort: requestId={}, status={}", 
				 requestId, res.toString());
	    return res;
	}
    }

    public String hexString(short[] p)
    {
	if (p == null) return null;

	String s = "hex: ";
	for (int i = 0; i < p.length; i++) {
	    s += "0x" + RequestSetV3.Short2Hex(p[i]) + ", ";
	}
	s += "\n";
	return s;
    }

    ByteBuffer buf = null;

    public synchronized int recvShort(short[] param)
    {
	if (! isOpen()) return -1;
	if (param == null) return -1; // error

	int length = param.length;
	int bytes = length * 2;
	if (buf == null || buf.limit() != bytes) buf = ByteBuffer.allocate(bytes);

	if (DEBUG) log.info("readShort: try read {} bytes", bytes);
	if (DEBUG) log.info("readShort: try read {} short: channel={}", length, channel.toString());

	int rr = 0;
	try {
	    rr = channel.read(buf);
	    if (DEBUG) log.info("AppControllerV3:recvShort: read = {} bytes.", rr);
	} catch (Exception ex) {
	    if (DEBUG) log.info("AppControllerV3:recvShort: ex = {}.", ex);
	}

	if (rr == -1) {
	    if (DEBUG) log.info("AppControllerV3:recvShort: close by return={} .", rr);
	    close();
	    return -1;
	}
	
	if (buf.position() != bytes) return 0; // continue
	
	buf.flip();
	for (int i = 0; i < length; i++) {
	    param[i] = buf.getShort();
	}
	if (DEBUG) log.info("AppControllerV3:recvShort: read done, buf capacity={} .", 
			    buf.capacity());
	buf.clear();

	return length;
    }

    public void ssleep(long sec) {
	try {
	    Thread.sleep(sec * 1000);
	} catch (InterruptedException e) {
	    e.printStackTrace();
	}
    }

    private short[] head = null;
    private short[][] param = null;
    private boolean recv_head_done = false;

    private int recv_pnum = 0;
    private int recv_pnum_done = 0;
    private int recv_requestId = 0;

    private synchronized int do_recv() throws IOException
    {
	if (DEBUG) log.info("AppControllerV3: do_recv: enter.");
	if (! isOpen()) return -1;

	if (head == null) head = new short[HeadLength];

	if (! recv_head_done) {
	    int len = recvShort(head);
	    if (DEBUG2) log.info("do_recv: head read == {}.", len);
	    if (len == -1) {
		if (DEBUG) log.info("do_recv: head read == -1, as Socket Close.");
		close();
		return -1;
	    } else if (len == HeadLength) {
		int version = Short.toUnsignedInt(head[0]);
		recv_pnum = Short.toUnsignedInt(head[1]);
		recv_requestId = Short.toUnsignedInt(head[2]);
		recv_pnum_done = 0;
		param = new short[recv_pnum][PacketLength];
		
		recv_head_done = true;
		if (DEBUG) log.info("do_recv: head read done..");
	    } else return 0; // continue
	}
	if (DEBUG) log.info("AppControllerV3: do_recv: head read done.");

	for (int i = recv_pnum_done; i < recv_pnum; i++) {
	    int len = recvShort(param[i]);
	    if (DEBUG2) log.info("do_recv: param i = {}, len = {}.", i, len);
	    if (len == -1) {
		if (DEBUG) log.info("do_recv: pnum read == -1, as Socket Close.");
		close();
		return -1;
	    }
	    if (len == 0) return 0;
	    if (len == PacketLength) recv_pnum_done = i;
	    if (DEBUG) log.info("AppControllerV3: do_recv: done i = {}.", i);
	}

	Integer key = new Integer(recv_requestId);
	if (DEBUG) log.info("do_recv: key = {}.", key);
	if (DEBUG) log.info("AppControllerV3: do_recv: pnum = {}.", recv_pnum);

	ResponseSetV3 res = null;
	try {
	    res = (ResponseSetV3) ResponseSetV3.responses.get(key);
	    if (res == null) {
		if (DEBUG) log.info("do_recv: res == null");
		throw new Exception("ERROR in protocolV3 in management of Hashtable.");
	    }
	    res.setResponse(head, param);

	    if (DEBUG) log.info("do_recv: res = {}.", res.toString());
	} catch (Exception ex) {
	    if (DEBUG) log.info("do_recv: ex = {}.", ex);
	    return -1;
	}
	
	if (DEBUG) log.info("do_recv: key = {}.", key);
	if (DEBUG) log.info("do_recv: boolean = {}.", 
			     ResponseSetV3.responses.containsKey(key));
	
	if (DEBUG2) log.info("do_recv #3: if RECV_THREAD: notifyAll() key = {}.", key);
	if (res.checkRecvDone()) {
	    if (DEBUG2) log.info("do_recv #0: if RECV_THREAD: notifyAll() key = {}.", key);
	    if (DO_RECV_THREAD) {
		/*
		if (ResponseWait) {
		    res = (ResponseSetV3) ResponseSetV3.responses.get(key);
		    ResponseSetV3.responses.remove(key);
		} 
		*/
		if (DEBUG2) log.info("do_recv #1: if RECV_THREAD: notifyAll() key = {}.", key);
		notifyAll();
	    } 
	    
	    recv_pnum = 0;
	    recv_pnum_done = 0;
	    recv_requestId = 0;

	    head = null;
	    param = null;
	    recv_head_done = false;
	}
	return 1;
    }
    
    private synchronized void do_write()
    {
	return;
    }

    public short[] getStatusShort() 
    {
	int len = HeadLength + PacketLength;
	short[] param = new short[len];
	short[] paramh = new short[HeadLength];
	short[] paramb = new short[PacketLength];
	for (int i = 0; i < len; i++) {
	    param[0] = (short) 0;
	}

	param[0] = (short) 3; // Version
	param[1] = (short) 1; // ParameterNum
	param[2] = (short) getRequestId();
	param[7] = (short) 3; // getStatus

	return param;
    }

    public synchronized short[] getStatusShort_Blocking() 
    {
	short[] param = getStatusShort();
	ResponseSetV3 res = sendShort(param);
	if (DEBUG) log.info("getStatusShort:send done. key={}", param[2]);

	short[] rhead = new short[HeadLength];
	short[] rparam = new short[PacketLength];
	recvShort(rhead);
	if (DEBUG) log.info("getStatusShort:recv head.");
	recvShort(rparam);
	if (DEBUG) log.info("getStatusShort:recv status.");

	return rparam;
    }

    public void setStopThread() 
    {
	// stopThread = true;
    }

    public void run() 
    {
	stopThread = false;
	if (DEBUG) log.info("start thread.");
	int w = 1000; // sec

	try {
	    while(true) {
		int r = selector.select();
		if (DEBUG) log.info ("selector return now.");
		if (r == 0 && stopThread) {
		    close();
		    ssleep(1);
		    continue;
		}
		
		for (Iterator it = selector.selectedKeys().iterator(); it.hasNext();) {
		    SelectionKey key = (SelectionKey) it.next();
		    it.remove();

		    SocketChannel sc = (SocketChannel) key.channel();
		    if (sc != channel) {
			log.error("read channel {} != key.channel {}.", channel, sc);
			if (DEBUG) log.info("readable channel={}.", sc.toString());
			if (DEBUG) log.info("readable class channel ={}.", channel.toString());
			ssleep(1);
		    }
		    try {
			if (key.isReadable()) {
			    if (DEBUG) log.info("call do_recv.");
			    do_recv();
			    if (DEBUG) log.info("done do_recv.");
			} else if (key.isWritable()) {
			    /*
			    // write() is blocking I/O
			    if (DEBUG) log.info("call do_write.");
			    do_write();
			    if (DEBUG) log.info("done do_write.");
			    */
			} 
		    } catch (java.nio.channels.CancelledKeyException ex) {}
		}
	    }
	} catch (IOException e) {
	    e.printStackTrace();
	} finally {
	    if (channel != null && channel.isOpen()) {
		try {
		    channel.close();
		    stopThread = true;
		} catch (IOException e) {}
	    }
	}
    }
}
