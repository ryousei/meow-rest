/*
 * Copyright (c)2022 National Institute of Advanced Industrial Science 
 * and Technology (AIST). All rights reserved.
 */

/*
 * Copyright 2019-present Open Networking Foundation
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
package org.meow.app;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onosproject.rest.AbstractWebResource;

import java.util.concurrent.CompletableFuture;

import javax.ws.rs.GET;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.PathParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.MediaType;

import static org.onlab.util.Tools.nullIsNotFound;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;

import org.json.JSONObject;
import org.json.JSONArray;
import org.yaml.snakeyaml.Yaml;

import java.util.Hashtable;
import java.util.Enumeration;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.onlab.packet.IpAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.InetAddress;

/**
 * meow controller web resource.
 */
@Component(immediate = true)
@Path("controller")
public class AppWebResource extends AbstractWebResource {
    static public String LOCK = "LOCK";
    
    static private final Logger log = LoggerFactory.getLogger(AppWebResource.class);
    // private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AppWebResource.class);
    static public final String APPLICATION_YAML = "application/json";
    private int swSize = 0; // switch NxN N=size.

    static public final boolean DEBUG = false;
    static public final boolean DEBUG2 = false;
    static public final boolean IFUPDOWN = true;

    static public int PORT_offset = 0; // start from port offset
    static public int OSW_offset = 0; // start from optical sw offset
    static public int LSW_offset = 0; // start from lambda sw offset
    static public int MA_offset = 0; // start from master offset

    static public final int ForwardDirectionPath = 1;
    static public final int ReverseDirectionPath = 2;


    // public static final int P_Master = 0;
    public static final int P_SubMaster = 0;
    public static final int P_Slave = 1;
    public static final int P_Sport = 2;
    public static final int P_Dport = 3;
    public static final int P_Wave = 4;
    public static final int P_N5 = 5;
    public static final int P_N6 = 6;
    public static final int P_N7 = 7;
    public static final int P_ID= 7;

    static final int START_TIME		= 0;
    static final int CREATE_RequestSet	= 1;
    static final int LOCK_AND_CHECK	= 2;
    static final int ALLOC_TIME		= 3;
    static final int SET_PARAMS_TIME	= 4;
    static final int SET_SW_TIME	= 5;
    static final int SET_TOS_TIME	= 6;
    static final int N_TIME		= 7;
    long[] atime = new long[N_TIME];

    public static final int PacketLength = RequestV3.PacketLength;

    public static final String debug_user = "okazaki";
    // public static final String hostlist_name = ".hostlist";

    public static AppControllerV3[][][] controls = null;

    private static int gidno = 0;
    private synchronized static String getGID() {
        String gid = "GID00" + gidno;
	gidno ++;
	return gid;
    }

    private static Hashtable<String, RequestSetV3> gid2reqSet = 
	new Hashtable<String, RequestSetV3>();

    public static long getMicrosec() {
	long ntime = System.nanoTime();
	// long mtime = (ntime/1000) % (60L * 1000000L);
	long mtime = (ntime/1000);
	return mtime;
    }

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    static final int MAX_N_PLANE = 32;
    static int N_Plane = MAX_N_PLANE;
    static final int MAX_Master = 8;	// MWOE board
    static int N_Master = MAX_Master;
    static int N_PORT = 0;
    static public final int MAX_SubMaster = 7;
    static int N_SubMaster = 0;
    static public boolean[][] isSubMaster = new boolean[MAX_Master][MAX_SubMaster];

    static int[] tos = new int[N_Plane];
    static boolean [][] ports = null;

    public static int getTos(int plane) {
	return tos[plane];
    }

    public static JSONObject getOswObject(int id) {
	if (osws == null) return null;
	if (DEBUG) log.info("AppWebResource: getOswObject: id={}", id);
	return osws[id];
    }

    public static JSONObject getLswObject(int id) {
	if (lsws == null) return null;
	return lsws[id];
    }

    static long snum = 0;
    static int pnum = 0;

    static String dummyAddr = "127.0.0.1";
    static int dummyPort = 33101;

    static int[] lsw2masterId;
    static int[] lsw2subMasterId;
    static int[] osw2masterId;
    static int[] osw2subMasterId;

    static JSONObject[] lsws = null;
    static JSONObject[] osws = null;
    static JSONObject[][] masters = null;
    static JSONObject[] devices = null;
    static String[] port2MasterNode = null;
    static String[] port2Subnet = null;
    static int[] rack2txPort = null;
    static int[] rack2rxPort = null;
    static int[] port2Rack = null;
    
    static boolean DuplicatePathCheck = true;

    static int[][] fwOswId = null;
    static int[][] fwOswInId = null;
    static int[][] fwOswOutId = null;
    static int[][] fwLswId = null;
    static int[][] fwLswInId = null;
    static int[][] fwLswOutId = null;
    static int[][] fwLswLambdaId = null;

    static int[][] bkOswId = null;
    static int[][] bkOswInId = null;
    static int[][] bkOswOutId = null;
    static int[][] bkLswId = null;
    static int[][] bkLswInId = null;
    static int[][] bkLswOutId = null;
    static int[][] bkLswLambdaId = null;

    static boolean table_config_done = false;
    static boolean table_lsw_device5_done = false;
    static boolean table_osw_device5_done = false;
    static boolean table_master5_done = false;

    static JSONObject[][] config = null;
    static JSONObject getConfig(int src, int dst) {
	return config[src][dst];
    }

    static int getTxPort(int rackId) {
	return rack2txPort[rackId];
    }

    static int getRxPort(int rackId) {
	return rack2rxPort[rackId];
    }

    static int getRackId(int port) {
	return port2Rack[port];
    }

    static String getSubnet(int port) {
	return port2Subnet[port];
    }

    static boolean isMarkPort (int plane, int port) {
	return  ports[plane][port];
    }

    static boolean markPort (int plane, int port) {
	if (ports[plane][port]) return false;

	ports[plane][port] = true;
	return true;
    }

    static boolean unmarkPort (int plane, int port) {
	ports[plane][port] = false;
	return true;
    }

    synchronized static long getSerial() {
	return ++snum;
    }

    synchronized static int getPacketId() {
	return ++pnum;
    }

    public void ssleep(long sec) {
	try {
	    Thread.sleep(sec * 1000);
	} catch (InterruptedException e) {
	    e.printStackTrace();
	}
    }

    public String getString(Exception ex) 
    {
	StackTraceElement[] stacks = ex.getStackTrace();
	String s = "";
	for (StackTraceElement element : stacks) {
	    s += "\n\t" + element ;
	}
	s += "\n\t";
	return s;
    }

    // private static DeviceProvider provider = null;

    /**
     * Get hello world greeting.
     *
     * @return 200 OK
     */
    @GET
    @Path("hello")
    public Response getGreeting() {
        if (true) {
            log.info("START i = {}", 0);
	    long t = 0L;
            for (int i = 0; i < 1000; i++) {
		t += getMicrosec();
                // log.info("hello:{}, @TIME:{}", "aho", getMicrosec());
                // log.info("hello:{}, @TIME:{}", "aho", i * 1L);
            }
            log.info("END i = {}", 1000);
            log.info("END i = {} t:{}", 1000, t);
        }

        ObjectNode node = mapper().createObjectNode().put("OK", "getGreeting hello");
        return ok(node).build();
    }

    /**
     * Get List control ids
     * @return 200 OK
     */
    @GET
    @Path("connect")
    public Response connectMeow() {
	connectMasters();

        ObjectNode node = mapper().createObjectNode().put("OK", "");
        return ok(node).build();
    }

    /**
     * Get List control ids
     * @return 200 OK
     */
    @GET
    @Path("reconnect")
    public Response reConnectMeow() {
	reConnectMasters();

        ObjectNode node = mapper().createObjectNode().put("OK", "");
        return ok(node).build();
    }

    /**
     * Get List control ids
     * @return 200 OK
     */
    @GET
    @Path("disconnect")
    public Response disConnectMeow() {
	disConnectMasters();

        ObjectNode node = mapper().createObjectNode().put("OK", "Disconnect Neow done.");
        return ok(node).build();
    }

    /**
     * Get List control ids
     * @return 200 OK
     */
    @GET
    @Path("gid")
    public Response getGids() {
	Set<String> gids = gid2reqSet.keySet();

        ObjectNode node = mapper().createObjectNode().put("OK", "" + gids);
        return ok(node).build();
    }

    /**
     * Get Path information
     * @return 200 OK
     */
    @GET
    @Path("gid/{groupId}")
    public Response getGidInfo(@PathParam("groupId") String gid) {
	if (DEBUG) log.info("getGidInfo: GID={}.", gid);
	RequestSetV3 rset  = gid2reqSet.get(gid);

	String s = "gid=" + gid + ": ";
	if (rset != null) {
	    s += rset.toString() + ",\n";
	} else {
	    s += "is not exist.\n";
	}
	
        ObjectNode node = mapper().createObjectNode().put
	    ("OK", "" + s);
        return ok(node).build();
    }

    /**
     * Change mode to check duplicate path 
     * @return 200 OK
     */
    @GET
    @Path("duplicate/{mode}")
    public Response setDuplicatePathCheck(@PathParam("mode") String mode) {
	switch (mode) {
	case "true":
	    DuplicatePathCheck = true;
	    break;

	case "false":
	    DuplicatePathCheck = false;
	    break;
	}

        ObjectNode node = mapper().createObjectNode().put
	    ("OK", "Check duplicat path is " + DuplicatePathCheck + ".");
        return ok(node).build();
    }

    /**
     * Change mode to check duplicate path 
     * @return 200 OK
     */
    // @Consumes("application/yaml")
    // @Produces(MediaType.APPLICATION_YAML)
    // @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON, RestServiceUtil.APPLICATION_YAML})
    @POST
    @Path("enablepaths")
    public Response enablePaths(String requests) {
        JSONObject reqs = new JSONObject(requests);
        JSONArray reqArray = reqs.getJSONArray("requests");
	String s = "";

	int reqSize = reqArray.length();
	for (int r = 0; r < reqSize; r ++) {
	    JSONObject req = reqArray.getJSONObject(r);
	    String dir = req.getString("dir");
	    boolean fw = false;
	    boolean bw = false;

	    if (dir == null || dir.equals("F") || dir.equals("B") 
		|| dir.equals(RequestV3.FW) || dir.equals(RequestV3.BI)) fw = true;
	    if (dir == null || dir.equals("R") || dir.equals("B") 
		|| dir.equals(RequestV3.BW) || dir.equals(RequestV3.BI)) bw = true;

	    String s_srcRack = req.getString("src");
	    String s_dstRack = req.getString("dst");

	    int srcRack = Integer.parseInt(s_srcRack.substring(2));
	    int dstRack = Integer.parseInt(s_dstRack.substring(2));
	    int srcPort = AppWebResource.getTxPort(srcRack);
	    int dstPort = AppWebResource.getRxPort(dstRack);
	    
	    JSONObject conf = getConfig(srcPort, dstPort);
	    if (fw) {
		conf.put("enable", 1);
		if (DEBUG) log.info("enablePaths: src={}, dst={}, enable.", 
				    srcPort, dstPort);
	    }
	    if (bw) {
		conf.put("rv_enable", 1);
		if (DEBUG) log.info("enablePaths: bw src={}, dst={}, enable.", 
				    srcPort, dstPort);
	    }
	    s += "enable from " + srcPort + " to " + dstPort + " direction: " + 
		dir + ". \n  ";
	}

	ObjectNode node = mapper().createObjectNode().put("OK", s);
	return ok(node).build();
    }

    /**
     * Change mode to check duplicate path 
     * @return 200 OK
     */
    // @Consumes("application/yaml")
    // @Produces(MediaType.APPLICATION_YAML)
    // @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON, RestServiceUtil.APPLICATION_YAML})
    @POST
    @Path("disablepaths")
    public Response disablePaths(String requests) {
        JSONObject reqs = new JSONObject(requests);
        JSONArray reqArray = reqs.getJSONArray("requests");
	String s = "";

	int reqSize = reqArray.length();
	for (int r = 0; r < reqSize; r ++) {
	    JSONObject req = reqArray.getJSONObject(r);
	    String dir = req.getString("dir");
	    boolean fw = false;
	    boolean bw = false;

	    if (dir == null || dir.equals("F") || dir.equals("B") 
		|| dir.equals(RequestV3.FW) || dir.equals(RequestV3.BI)) fw = true;
	    if (dir == null || dir.equals("R") || dir.equals("B") 
		|| dir.equals(RequestV3.BW) || dir.equals(RequestV3.BI)) bw = true;

	    String s_srcRack = req.getString("src");
	    String s_dstRack = req.getString("dst");

	    int srcRack = Integer.parseInt(s_srcRack.substring(2));
	    int dstRack = Integer.parseInt(s_dstRack.substring(2));
	    int srcPort = AppWebResource.getTxPort(srcRack);
	    int dstPort = AppWebResource.getRxPort(dstRack);
	    
	    JSONObject conf = getConfig(srcPort, dstPort);
	    if (fw) {
		conf.put("enable", 0);
		if (DEBUG) log.info("disablePaths: src={}, dst={}, disable.", 
				    srcPort, dstPort);
	    }
	    if (bw) {
		conf.put("rv_enable", 0);
		if (DEBUG) log.info("disablePaths: bw src={}, dst={}, disable.", 
				    srcPort, dstPort);
	    }
	    s += "disable from " + srcPort + " to " + dstPort + " direction: " + 
		dir + ". \n  ";
	}

	ObjectNode node = mapper().createObjectNode().put("OK", s);
	return ok(node).build();
    }

    /**
     * Change mode to check duplicate path 
     * @return 200 OK
     */
    @GET
    @Path("getStatus")
    public Response getSataus() {
	String rs = "";

	if (controls == null) {
	    ObjectNode node = mapper().createObjectNode().put
		("NG", "Status: controls == null\n");
	    return ok(node).build();
	}

	for (int p = 0; p < N_Plane; p++) {
	    for (int m = 0; m < N_Master; m++) {
		for (int s = 0; s < N_SubMaster; s++) {
		    AppControllerV3 control = controls[p][m][s];
		    if (control == null) continue;

		    synchronized (control) {
			short[] param = control.getGetStatusRequest();
			/*
			try {
			    ResponseSetV3 res = new ResponseSetV3(param, null, p, m, s);
			    if (DEBUG) log.info("getStatis: get res={}", res);
			    ResponseSetV3 r = control.sendShort(param);
			    if (DEBUG) log.info("getStatis: get res={}", res);
			    rs += "\t " + r.toString();
			    if (DEBUG) log.info("getStatis: get retuen res={}", r);
			} catch (Exception ex) {
			    ex2log(ex);
			}
			*/

			// ResponseSetV3 r = control.sendShort(param);
			// short[] param = control.getGetStatusRequest();
			short[] res = control.getStatusShort_Blocking();

			rs += "\t[" + p + "][" + m + "][" + s + "]: ";
			rs += control.hexString(res);
			// for (int i = 0; i < PacketLength; i++) {}
		    }
		}
	    }
	}

        ObjectNode node = mapper().createObjectNode().put
	    ("OK", "Status:\n" + rs);
        return ok(node).build();
    }

    /**
     * DELETE Path
     * @return 200 OK
     */
    @DELETE
    @Path("gid/{controlId}")
    public Response removePaths(@PathParam("controlId") String gid) {
	RequestSetV3 rsets = gid2reqSet.remove(gid);

	if (rsets == null) {
	    ObjectNode node = mapper().createObjectNode().put
		("OK:deletePaths:", gid + " is not exist.");
	    return ok(node).build();
	}

	synchronized (LOCK) {
	    rsets.unsetToS();
	    rsets.joinToS();
	    try {
		rsets.unsetSW();
	    } catch (Exception ex) {
		if (DEBUG) log.info("AppWebResource: unsetSW(): ex={}.", ex);
	    }
	    rsets.unmarkPath();
	}

	String downtime = null;
	if (IFUPDOWN) {
	    SpawnExpect se = new SpawnExpect();
	    se.setDown();
	    se.start();
	    while (true) {
		try {
		    se.join();
		    break;
		} catch (InterruptedException e) {}
	    }
	    downtime = se.getOutput();
	    if (DEBUG) log.info("Time of interface down from up is {}.", downtime);
	}

	ObjectNode node = mapper().createObjectNode().put
	    ("OK:removePaths", gid + " is remove now." + " " + downtime);
	return ok(node).build();
    }

    /**
     * UPDATE Path information
     * @return 200 OK
     */
    @POST
    @Path("updatepaths")
    @Consumes("application/json")
    @Produces(MediaType.APPLICATION_JSON)
    public Response updatePath(String requests) {
	ObjectNode node = mapper().createObjectNode()
	    .put("NG", "This updatePaths is not supported.");
        return ok(node).build();
    }

    /**
     * set new Path information
     * @return 200 OK
     */
    // @Consumes("application/yaml")
    // @Produces(MediaType.APPLICATION_YAML)
    // @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON, RestServiceUtil.APPLICATION_YAML})
    @POST
    @Path("settorus")
    @Consumes("application/json")
    @Produces(MediaType.APPLICATION_JSON)
    public Response setRingTours(String requests) {
	//Response resp = addPaths(String requests);
	ObjectNode node = mapper().createObjectNode()
	    .put("NG", "This updatePaths is not supported.");
	return ok(node).build();
    }

    /**
     * set new Path information
     * @return 200 OK
     */
    // @Consumes("application/yaml")
    // @Produces(MediaType.APPLICATION_YAML)
    // @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON, RestServiceUtil.APPLICATION_YAML})
    @POST
    @Path("setpaths")
    @Consumes("application/json")
    @Produces(MediaType.APPLICATION_JSON)
    public Response addPaths3(String requests) {
	atime[START_TIME] = getMicrosec();

        JSONObject reqs = new JSONObject(requests);
        JSONArray reqArray = reqs.getJSONArray("requests");
	RequestSetV3 rset = null;
	
	try {
	    rset = new RequestSetV3(reqs, PORT_offset,
				    N_Plane, N_Master, N_SubMaster, controls);
	    if (DEBUG) log.info("setpaths: #1 rset={}.", "" + rset);
	} catch (Exception ex) {
	    ObjectNode node = mapper().createObjectNode()
		.put("NG", "Exception=" + ex);
	     // .put("NG", "Exception=" + ex + ", \n" + getString(ex));
	    return ok(node).build();
	}
	atime[CREATE_RequestSet] = getMicrosec();

	synchronized (LOCK) {
	    if (! rset.markPath()) {
		ObjectNode node = mapper().createObjectNode()
		    .put("NG", "There are no free planes.");
		return ok(node).build();
	    }
	}
	atime[LOCK_AND_CHECK] = getMicrosec();

	String gid = getGID();
	gid2reqSet.put(gid, rset);
	atime[ALLOC_TIME] = getMicrosec();

	rset.setParams();
	atime[SET_PARAMS_TIME] = getMicrosec();

	try {
	    if (DEBUG) log.info("AppWebResource: call setSW now.");
	    rset.setSW();
	    if (DEBUG) log.info("AppWebResource: return setSW now.");
	} catch (Exception ex) {
	    if (DEBUG) log.info("AppWebResource: setSW(): ex={}.", ex);
	    ObjectNode node = mapper().createObjectNode()
		.put("NG", "setSW(): Exception=" + ex);
	    return ok(node).build();
	}
	atime[SET_SW_TIME] = getMicrosec();

	String uptime = null;
	if (IFUPDOWN) {
	    SpawnExpect se = new SpawnExpect();
	    se.setUp();
	    se.start();
	    while (true) {
		try {
		    se.join();
		    break;
		} catch (InterruptedException e) {}
	    }
	    uptime = se.getOutput();

	    log.info("Time of interface up from down is {}.", uptime);
	}

	rset.setToS();
	rset.joinToS();
	atime[SET_TOS_TIME] = getMicrosec();

	String s = "";
	if (DEBUG2) {
	    s = "";
	    for (int i = 1; i < N_TIME; i++) {
		s += (atime[i] - atime[i-1]) + ", " ;
	    }
	    s += (atime[N_TIME-1] - atime[0]) + ", " ;
	    s += " GID = " + gid + ".";

	    log.info("AppWebResource:{}", s);
	} else {
	    s = "GID = " + gid + ".";
	}
	s += " uptime is " + uptime;

	ObjectNode node = mapper().createObjectNode().put("OK", s);
	return ok(node).build();
    }

    /**
     * set new Path information
     * @return 200 OK
     */
    // @Consumes("application/yaml")
    // @Produces(MediaType.APPLICATION_YAML)
    // @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON, RestServiceUtil.APPLICATION_YAML})
    @POST
    @Path("setpath")
    @Consumes("application/json")
    @Produces(MediaType.APPLICATION_JSON)
    public Response setPath(String request) {
	atime[START_TIME] = getMicrosec();

	String err = "This REST interface is old. not supported now.";
	ObjectNode node = mapper().createObjectNode().put("NG", err);

	return ok(node).build();
    }

    /**
     * set tables for config
     * @return 200 OK
     * ## Done ##
     */
    @POST
    @Path("table5")
    @Consumes("application/json")
    @Produces(MediaType.APPLICATION_JSON)
    public Response setTable(String request) {
	String type = null;

	try {
	    log.debug("set table #5");
	    JSONObject json = new JSONObject(request);
	    type = json.getString("type");
	    JSONArray table = json.getJSONArray("table");

	    if (type == null || type.equals("")) {
		ObjectNode node = mapper().createObjectNode().put
		    ("NG", "Bad format(setTable): check type or size key.");
		return ok(node).build();
	    } 

	    int c_length = 0;
	    int osw_offset = 0;
	    int lsw_offset = 0;
		
	    switch (type) {
	    case "config":
		table_config_done = true;
		c_length = json.getInt("length");

		int port_offset = json.getInt("port_offset");
		if (PORT_offset == 0) {
		    PORT_offset = port_offset;
		} else {
		    if (PORT_offset != port_offset) {
			ObjectNode node = mapper().createObjectNode().put
			    ("NG", "Bad format(config): check port_offset.");
			return ok(node).build();
		    }
		}

		osw_offset = json.getInt("osw_offset");
		if (OSW_offset == 0) {
		    OSW_offset = osw_offset;
		} else {
		    if (OSW_offset != osw_offset) {
			ObjectNode node = mapper().createObjectNode().put
			    ("NG", "Bad format(config): check osw_offset, " +
			     OSW_offset + ", " + osw_offset);
			return ok(node).build();
		    }
		}

		lsw_offset = json.getInt("lsw_offset");
		if (LSW_offset == 0) {
		    LSW_offset = lsw_offset;
		} else {
		    if (LSW_offset != lsw_offset) {
			ObjectNode node = mapper().createObjectNode().put
			    ("NG", "Bad format(config): check lsw_offset.");
			return ok(node).build();
		    }
		}

		switch (c_length) {
		case 3600:
		    swSize = 60 * 2;	// inport:60, outport:60
		    break;
		case 900:
		    swSize = 30 * 2;	// inport:30, outport:30
		    break;
		case 16:
		    swSize = 4 * 2;	// inport:4, outport:4
		    N_PORT = swSize;
		    break;
		default:
		    ObjectNode node = mapper().createObjectNode().put
			("NG", "Bad format: unknown length(2.2, 3 is 3600 (60x60), 1.2 is 900 (30x30), 5 is 16 (4x4).");
		    return ok(node).build();
		}
		
		if (DEBUG) log.info("set table5: type is {}, swSize is {}.", type, swSize);

		config = new JSONObject[swSize][swSize];
		for (int i = 0; i < c_length; i++) {
		    JSONObject o = table.getJSONObject(i);
		    if (DEBUG) log.info("config o: {}.", o);
		    o.put("enable", 1);
		    o.put("rv_enable", 1);
		    try {
			int src = o.getInt("src");
			int dst = o.getInt("dst");
			config[src][dst] = o;
		    } catch (org.json.JSONException ex) {
			if (DEBUG) log.info("set table5: getInt(src/dst) is missing.");
		    }
		}

		if (DEBUG) {
		    for (int i = 0; i < swSize/2; i++) {
			for (int j = swSize/2; j < swSize; j++) {
			    log.info("config[{}][{}]: {}.\n", i, j, config[i][j]);
			}
		    }
		}
		{
		    ObjectNode node = mapper().createObjectNode()
			.put("OK", "setTable:config");
		    return ok(node).build();
		}
		// break; // unreachable statement

	    case "lsw-device5":
		int lsw_length = json.getInt("length");

		lsw_offset = json.getInt("lsw_offset");
		if (LSW_offset == 0) {
		    LSW_offset = lsw_offset;
		} else {
		    if (LSW_offset != lsw_offset) {
			ObjectNode node = mapper().createObjectNode().put
			    ("NG", "Bad format(lsw-device5): check lsw_offset.");
			return ok(node).build();
		    }
		}
		
		lsws = new JSONObject[lsw_length];
		lsw2masterId = new int[lsw_length];
		lsw2subMasterId = new int[lsw_length];

		for (int i = 0; i < lsw_length; i++) {
		    JSONObject o = table.getJSONObject(i);
		    int sw_id = o.getInt("SwID");
		    lsws[sw_id] = o;
		    int m = o.getInt("MasterID");
		    lsw2masterId[sw_id] = m;
		    int s = o.getInt("SubMasterID");
		    lsw2subMasterId[sw_id] = s;

		    if (s < MAX_SubMaster) {
			if (s+1 > N_SubMaster) N_SubMaster = s+1;
			isSubMaster[m][s] = true;
		    } else {
			ObjectNode node = mapper().createObjectNode().put
			    ("NG", "Bad format(lsw-device5): SubMasterID > N_SubMaster(6).");
			return ok(node).build();
		    }
		}
		table_lsw_device5_done = true;
		if (DEBUG) log.info("N_SubMaster = {}\n", N_SubMaster);

		if (table_lsw_device5_done && table_osw_device5_done && table_master5_done) {
		    reConnectMasters();
		}

		{
		    ObjectNode node = mapper().createObjectNode()
			.put("OK", "setTable:lsw-device5");
		    return ok(node).build();
		}
		// break; // unreachable statement

	    case "osw-device5":
		int osw_length = json.getInt("length");

		osw_offset = json.getInt("osw_offset");
		if (OSW_offset == 0) {
		    OSW_offset = osw_offset;
		} else {
		    if (OSW_offset != osw_offset) {
			ObjectNode node = mapper().createObjectNode().put
			    ("NG", "Bad format(osw-device5): check osw_offset.");
			return ok(node).build();
		    }
		}

		osws = new JSONObject[osw_length];
		osw2masterId = new int[osw_length];
		osw2subMasterId = new int[osw_length];

		for (int i = 0; i < osw_length; i++) {
		    JSONObject o = table.getJSONObject(i);
		    int sw_id = o.getInt("SwID");
		    osws[sw_id] = o;
		    int m = o.getInt("MasterID");
		    osw2masterId[sw_id] = m;
		    // int s = o.getInt("SubMasterID");
		    int s = 0;
		    osw2subMasterId[sw_id] = s;
		    
		    if (s <= N_SubMaster) {
			if (s+1 > N_SubMaster) N_SubMaster = s+1;
			isSubMaster[m][s] = true;
		    } else {
			if (DEBUG) log.info("MasterID={}. N_SubMaster={}, s={}\n",
					    N_Master, N_SubMaster, s);
			ObjectNode node = mapper().createObjectNode().put
			    ("NG", "Bad format(osw-device5): MasterID => N_Master || SubMasterID => N_SubMaster(7).");
			return ok(node).build();
		    }
		}
		table_osw_device5_done = true;
		if (DEBUG) log.info("N_SubMaster = {}\n", N_SubMaster);

		if (table_lsw_device5_done && table_osw_device5_done && table_master5_done) {
		    reConnectMasters();
		}

		{
		    ObjectNode node = mapper().createObjectNode()
			.put("OK", "setTable:osw-device5");
		    return ok(node).build();
		}
		// break; // unreachable statement

	    case "master5":
		int t_length = json.getInt("t_length");
		int p_length = json.getInt("p_length");
		int m_length = json.getInt("m_length");
		int s_length = N_SubMaster;

		MA_offset = json.getInt("master_offset");
		N_Master = m_length;
		if (DEBUG) log.info("t_length = {}, p_length = {}, m_length = {}, ma_offset = {} \n", t_length, p_length, m_length, MA_offset);

		if (p_length > N_Plane) {
		    ObjectNode node = mapper().createObjectNode().put
			("NG", "master5: Bad format(master5): p_length and N_Plane.");
		    return ok(node).build();
		}

		N_Plane = p_length;
		tos = new int[N_Plane];
		for (int i = 0; i < N_Plane; i++) {
		    ports = new boolean[N_Plane][N_PORT];
		    tos[i] = 0x04 * (i + 1);
		}

		N_Master = m_length;
                masters = new JSONObject[N_Plane][N_Master];
                for (int i = 0; i < t_length; i++) {
                    JSONObject o = table.getJSONObject(i);
                    int planeID = o.getInt("PlaneID");
                    int masterId = o.getInt("MasterID") - MA_offset;
		    if (DEBUG) log.info("PlaneID = {}, MasterID = {}", planeID, masterId);
                    masters[planeID][masterId] = o;
                }
		table_master5_done = true;

		if (table_lsw_device5_done && table_osw_device5_done && table_master5_done) {
		    reConnectMasters();
		}
		if (DEBUG) log.info("reConnectMasters(): done");

		// deviceIdMAP = new DeviceId[N_Master];

		{
		    ObjectNode node = mapper().createObjectNode()
			.put("OK", "setTable:master5");
		    return ok(node).build();
		}
		// break; // unreachable statement
		
	    case "port-rack5":
		c_length = json.getInt("length");
		if (N_PORT != c_length) {
		    ObjectNode node = mapper().createObjectNode().put
			("NG", "Port size is mismatch in config table or in port table.");
		    return ok(node).build();
		}
		port_offset = json.getInt("port_offset");
		if (PORT_offset == 0) {
		    PORT_offset = port_offset;
		} else {
		    if (PORT_offset != port_offset) {
			ObjectNode node = mapper().createObjectNode().put
			    ("NG", "Bad format(port_rack5): check port_offset.");
			return ok(node).build();
		    }
		}

		port2MasterNode = new String[c_length];
		port2Subnet = new String[c_length];
		rack2txPort = new int[c_length]; // c_length/2
		rack2rxPort = new int[c_length]; // c_length/2
		port2Rack = new int[c_length];

		for (int i = 0; i < c_length; i++) {
		    JSONObject o = table.getJSONObject(i);
		    int portId = o.getInt("PortID");
		    int rackId = o.getInt("RackID");
		    port2MasterNode[portId] = o.getString("MasterNode");
		    port2Subnet[portId] = o.getString("SubNetwork");
		    port2Rack[portId] = rackId;

		    String portType = o.getString("Type");
		    if (portType.equals("TX")) {
			rack2txPort[rackId] = portId;
		    } else if (portType.equals("RX")) {
			rack2rxPort[rackId] = portId; 
		    } else {
			ObjectNode node = mapper().createObjectNode().put
			    ("NG", "Bad port type formatin port_rack5.: check type (" + portType + ").");
			return ok(node).build();
		    }
		}
		{
		    ObjectNode node = mapper().createObjectNode()
			.put("OK", "setTable:port-rack5");
		    return ok(node).build();
		}
		// break; // unreachable statement

	    default:
		ObjectNode node = mapper().createObjectNode().put
		    ("NG", "Bad format(no table type): check json array part. table=" + type);
		return ok(node).build();
	    }
	} catch (Exception ex) {
	    StackTraceElement[] stacks = ex.getStackTrace();
	    int i = 0;
	    for (StackTraceElement element : stacks) {
		if (DEBUG) log.info("trace=" + element);
		if (i > 5) break;
		i++;
	    }
	    
	    if (true) {
		log.info("trace=" + stacks[0]);
		log.info("trace=" + stacks[1]);
	    }

	    ObjectNode node = mapper().createObjectNode().put
		("NG", "Bad format: Ex = " + ex.getMessage() + ", type: " + type);
	    return ok(node).build();
	}

	// unreachable statement
	// ObjectNode node = mapper().createObjectNode().put("OK", "setTable");
        // return ok(node).build();
    }

    public void disConnectMasters()
    {
	if (controls == null) return;

	// int mlen = masters[0].length;
	if (DEBUG) log.info("disConnectMaster: plane = {}, len = {}", N_Plane, N_Master);
	for (int p = 0; p < N_Plane; p++) {
	    for (int m = 0; m < N_Master; m++) {
		for (int s = 0; s < N_SubMaster; s++) {
		    if (DEBUG) log.info("disConnectMaster: plane:{}, master:{} subMaster:{}\n", p, m, s);
		    AppControllerV3 control = controls[p][m][s];

		    if (control == null) continue;
		    if (DEBUG) log.info("disConnectMaster:{}", "" + control);

		    control.setStopThread();
		    control.close();
		    controls[p][m][s] = null;
		}
	    }
	} 
	controls = null;
    }

    public void reConnectMasters()
    {
	disConnectMasters();
	connectMasters();
    }

    public void connectMasters() {
	if (controls != null) disConnectMasters();

	controls = new AppControllerV3[N_Plane][N_Master][N_SubMaster];
	if (DEBUG) log.info("reConnectMasters] new planes:{}, masters:{}, subMasters{}", N_Plane, N_Master, N_SubMaster);

	for (int p = 0; p < N_Plane; p ++) {
	    for (int m = 0; m < N_Master; m++) {
		String user = masters[p][m].getString("user");
		String pass = masters[p][m].getString("pass");
		String addr = masters[p][m].getString("IP");
		int port = masters[p][m].getInt("port");

		if ((addr == null) || addr.equals("")) {
		    if (DEBUG) log.info("skip connect to master] {}:{}\n", addr, port);
		    continue;
		}

		int sport = port;
		for (int s = 0; s < N_SubMaster; s++) {
		    sport += s;

		    if (! isSubMaster[m][s]) continue;

		    // addr = InetAddress.getByName(addr).getHostAddress();
		    // masters[p][i].put("addr", addr);

		    if (controls[p][m][s] != null) continue;

		    AppControllerV3 control = new AppControllerV3(addr, sport);
		    if (! control.isOpen()) {
			if (DEBUG) log.info("Fail to connect to master] {}:{}", addr, sport);
			continue;
		    }

		    if (RequestSetV3.DO_RECV_THREAD) {
			if (DEBUG) log.info("AppWebResource: AppControllerV3: start");
			control.start();
		    }
		    controls[p][m][s] = control;
		}
	    }
	}
    }

    public void ex2log(Exception ex)
    {
	StackTraceElement[] stacks = ex.getStackTrace();
	int i = 0;
	for (StackTraceElement element : stacks) {
	    if (DEBUG) log.info("trace=" + element);
	    if (i > 5) break;
	    i++;
	}
	
	if (DEBUG) {
	    log.info("trace=" + stacks[0]);
	    log.info("trace=" + stacks[1]);
	}
    }
}
