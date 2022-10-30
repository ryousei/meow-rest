/*
 * Copyright (c)2022 National Institute of Advanced Industrial Science 
 * and Technology (AIST). All rights reserved.
 */

package org.meow.app;

import org.json.JSONObject;
import org.json.JSONArray;
import org.yaml.snakeyaml.Yaml;

import java.util.Hashtable;
import java.util.ArrayList;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestV3
{
    private static final Logger log = LoggerFactory.getLogger(RequestV3.class);
    public static final int HeadLength = 4;
    public static final int PacketLength = 8;

    private static final boolean DEBUG = false;
    private static final boolean DEBUG2 = true;

    public static final String FW = "FW"; // forward direction
    public static final String BW = "BW"; // backward direction
    public static final String BI = "BI"; // Both directions/bi-direction 
    // public static final boolean SetupTOS_JOIN = false;
    public static final boolean SetupTOS_JOIN = true;

    private JSONObject req = null;
    private int PORT_offset = 0;

    private String dir = null;
    
    protected boolean fw = false;
    protected boolean bw = false;
    protected int srcPort = -1;
    protected int dstPort = -1;
    protected JSONObject conf;
    private String user = null;

    private JSONArray hostlist;

    private int ifw_src = -1;
    private int ifw_dst = -1;
    private String fw_srcNW = null;
    private String fw_dstNW = null;
    private int fw_srcRack = -1;
    private int fw_dstRack = -1;

    private int ibw_src = -1;
    private int ibw_dst = -1;
    private String bw_srcNW = null;
    private String bw_dstNW = null;
    private int bw_srcRack = -1;
    private int bw_dstRack = -1;

    private int fwPlaneId = -1;
    private int bwPlaneId = -1;
    private int fwTos = -1;
    private int bwTos = -1;

    private Hashtable<Integer, String> rack2list =
	new Hashtable<Integer, String>();

    private String addFwCmd = null;
    private String addBwCmd = null;
    private String delFwCmd = null;
    private String delBwCmd = null;
    private int masterId = -1;

    private int N_Plane = 0;
    private int N_Master = 0;
    private short[] head = null;
    private short parameterId = 0;
    private int ncontrol = 0;

    // PID: packet id for debug
    // private static int PID = 0;
    private static short PID = 0;
    // private synchronized static int getPID() {
    private synchronized static short getPID() {
        return PID++;
    }

    static final int START_TIME		= 0;
    static final int CHECK_DIR		= 1;
    static final int READ_RACK		= 2;
    static final int CHECK_PORT		= 3;
    static final int READ_FW_PARAMS	= 4;
    static final int READ_BW_PARAMS	= 5;
    static final int SET_HOSTS		= 6;
    static final int N_TIME		= 7;
    long[] atime = new long[N_TIME];
    SetTos fw_direct = null;
    SetTos bw_direct = null;

    private static short requestId = 0;
    private static synchronized short getRequestId() {
	// return (short) Short.toUnsignedInt(requestId++);
	return requestId++;
    }

    public static long getMicrosec() {
	long ntime = System.nanoTime();
	// long mtime = (ntime/1000) % (60L * 1000000L);
	long mtime = (ntime/1000);
	return mtime;
    }

    public RequestV3 (JSONObject req, int PORT_offset,
		      int N_Plane, int N_Master, short parameterId) 
	throws Exception {

	atime[START_TIME] = getMicrosec();
	this.req = req;
	this.PORT_offset = PORT_offset;
	this.N_Plane = N_Plane;
	this.N_Master = N_Master;
	this.parameterId = parameterId;

	dir = req.getString("dir");
	if (dir == null || dir.equals("F") || dir.equals("B") 
	    || dir.equals(FW) || dir.equals(BI)) fw = true;
	if (dir == null || dir.equals("R") || dir.equals("B") 
	    || dir.equals(BW) || dir.equals(BI)) bw = true;

	if (fw == false && bw == false) {
	    String s = "This Direction Parameter " + dir + " is incorrect.";
	    throw new Exception(s);
	}
	atime[CHECK_DIR] = getMicrosec();
	
	String s_srcRack = req.getString("src");
	// int srcRack = Integer.parseInt(s_srcRack.replaceAll("[^0-9]+", ""));
	int srcRack = Integer.parseInt(s_srcRack.substring(2));
	// log.info("HostList:src rack:{}", srcRack);
	String s_dstRack = req.getString("dst");
	// int dstRack = Integer.parseInt(s_dstRack.replaceAll("[^0-9]+", ""));
	int dstRack = Integer.parseInt(s_dstRack.substring(2));
	// log.info("HostList:dst rack:{}", dstRack);
	atime[READ_RACK] = getMicrosec();

	if (DEBUG) log.info("RequestV3: constructer: req={}, src={}, dst={}.",
			    req, srcRack, dstRack);
	
	srcPort = AppWebResource.getTxPort(srcRack);
	dstPort = AppWebResource.getRxPort(dstRack);

	conf = AppWebResource.getConfig(srcPort, dstPort);
	if (DEBUG) log.info("RequestV3: constructer: config={}", conf);
	if (conf == null) {
	    String s = "Internal error. This PATH " + srcPort + " -> " +
		dstPort + " is not exist.";
	    throw new Exception(s);
	}
	if (fw) {
	    int enable = conf.getInt("enable");
	    if (enable != 1) {
		String s = "Internal error. This PATH " + srcPort + " -> " +
		    dstPort + " is disable now.";
		throw new Exception(s);
	    }
	}
	if (bw) {
	    int enable = conf.getInt("rv_enable");
	    if (enable != 1) {
		String s = "Internal error. This PATH " + dstPort + " -> " +
		    srcPort + " is disable now.";
		throw new Exception(s);
	    }
	}
	atime[CHECK_PORT] = getMicrosec();

	if (fw) {
	    ifw_src = conf.getInt("src");
	    ifw_dst = conf.getInt("dst");
	    fw_srcNW = AppWebResource.getSubnet(ifw_src);
	    fw_dstNW = AppWebResource.getSubnet(ifw_dst);
	    fw_srcRack = AppWebResource.getRackId(ifw_src);
	    fw_dstRack = AppWebResource.getRackId(ifw_dst);

	    if (DEBUG) log.info("fw_src:{}, fw_dst:{}", ifw_src, ifw_dst);
	}
	atime[READ_FW_PARAMS] = getMicrosec();

	if (bw) {
	    try {
		ibw_src = conf.getInt("rv_src");
		ibw_dst = conf.getInt("rv_dst");

		bw_srcNW = AppWebResource.getSubnet(ibw_src);
		bw_dstNW = AppWebResource.getSubnet(ibw_dst);
		bw_srcRack = AppWebResource.getRackId(ibw_src);
		bw_dstRack = AppWebResource.getRackId(ibw_dst);

		if (DEBUG) log.info("bw_src:{}, bw_dst:{}", ibw_src, ibw_dst);
	    } catch (Exception ex) {
		if (bw) {
		    String s = "This path has no reverse-direction.";
		    if (DEBUG) log.info("setPath(): {}, ex: {}", s, ex);
		    throw new Exception(s);
		}
	    }
	}
	atime[READ_BW_PARAMS] = getMicrosec();
	
	user = req.getString("user");

	hostlist = req.getJSONArray("hosts");
	setHosts();
	atime[SET_HOSTS] = getMicrosec();

	if (false) {
	    String s = "";
	    for (int i = 1; i < N_TIME; i++) {
		s += (atime[i] - atime[i-1]) + ", " ;
	    }
	    log.info("Time check: {}", s);
	}
    }

    boolean markPath () {
	if (DEBUG) log.info("Request3V: markPath(): config = {}", conf);
	if (DEBUG) log.info("Request3V: markPath():fw_src:{}, fw_dst:{}", 
			     ifw_src, ifw_dst);

	/* check Available Plane */
	if (fw) {
	    int p = 0; // plane
	    for (p = 0; p < N_Plane; p++) {
		if (DEBUG) log.info("RequestV3:availablePath():p={} in {}", p, N_Plane);
		if (DEBUG) log.info("ifw_src={}, {}", ifw_src, 
				     AppWebResource.isMarkPort(p, ifw_src));
		if (DEBUG) log.info("ifw_dst={}, {}", ifw_dst, 
				     AppWebResource.isMarkPort(p, ifw_dst));
		if (AppWebResource.isMarkPort(p, ifw_src)) continue;
		if (AppWebResource.isMarkPort(p, ifw_dst)) continue;
		break;
	    }
	    fwPlaneId = p;
	    if (DEBUG) log.info("markPath: fw select plane = {}", p);
	    if (fwPlaneId == -1 || fwPlaneId == N_Plane) {
		fwPlaneId = -1;
		if (DEBUG) log.info("This request can not setup for no available plane." +
				     " (request: " + req + ")");
		fwPlaneId = -1;
		return false;
	    }

	    if (! AppWebResource.markPort(fwPlaneId, ifw_src)) {
		fwPlaneId = -1;
		return false;
	    }
	    if (! AppWebResource.markPort(fwPlaneId, ifw_dst)) {
		AppWebResource.unmarkPort(fwPlaneId, ifw_src);

		fwPlaneId = -1;
		return false;
	    }
	    fwTos = AppWebResource.getTos(fwPlaneId);
	    setAddFwCmd();
	    setDelFwCmd();
	}

	if (bw) {
	    int p = 0; // plane
	    for (p = 0; p < N_Plane; p++) {
		if (DEBUG) log.info("RequestV3:availablePath():p={}", p);
		if (DEBUG) log.info("ibw_src={}, {}", ibw_src, 
				     AppWebResource.isMarkPort(p, ibw_src));
		if (DEBUG) log.info("ibw_dst={}, {}", ibw_dst, 
				     AppWebResource.isMarkPort(p, ibw_dst));
		if (AppWebResource.isMarkPort(p, ibw_src)) continue;
		if (AppWebResource.isMarkPort(p, ibw_dst)) continue;
		break;
	    }
	    bwPlaneId = p;
	    if (bwPlaneId == -1 || bwPlaneId == N_Plane) {
		bwPlaneId = -1;
		if (DEBUG) log.info("This request can not setup for no available plane." +
				     " (request: " + req + ")");
		bwPlaneId = -1;
		return false;
	    }

	    if (! AppWebResource.markPort(bwPlaneId, ibw_src)) {
		AppWebResource.unmarkPort(fwPlaneId, ifw_src);
		AppWebResource.unmarkPort(fwPlaneId, ifw_dst);

		bwPlaneId = -1;
		return false;
	    }
	    if (! AppWebResource.markPort(bwPlaneId, ibw_dst)) {
		AppWebResource.unmarkPort(fwPlaneId, ifw_src);
		AppWebResource.unmarkPort(fwPlaneId, ifw_dst);
		AppWebResource.unmarkPort(bwPlaneId, ibw_src);

		bwPlaneId = -1;
		return false;
	    }
	    bwTos = AppWebResource.getTos(bwPlaneId);
	    setAddBwCmd();
	    setDelBwCmd();
	}
	if (DEBUG) log.info("markPath(): fwPlaneId={}, bwPlaneId={}", 
			    fwPlaneId, bwPlaneId);
	return true;
    }

    boolean unmarkPath() {
	if (fw && (fwPlaneId != -1)) {
	    if (DEBUG) log.info("fwPlaneId:{}, fw_src:{}, fw_dst:{}",
				 fwPlaneId, ifw_src, ifw_dst);
	    AppWebResource.unmarkPort(fwPlaneId, ifw_src);
	    AppWebResource.unmarkPort(fwPlaneId, ifw_dst);

	    fwPlaneId = -1;
	}
	if (bw && (bwPlaneId != -1)) {
	    if (DEBUG) log.info("bwPlaneId:{}, bw_src:{}, bw_dst:{}",
				 bwPlaneId, ibw_src, ibw_dst);
	    AppWebResource.unmarkPort(bwPlaneId, ibw_src);
	    AppWebResource.unmarkPort(bwPlaneId, ibw_dst);

	    bwPlaneId = -1;
	}
	return true;
    }

    private String setHosts() {
	Hashtable<Integer, ArrayList> rack2hosts = 
	    new Hashtable<Integer, ArrayList>();
	ArrayList<String> hosts = null;

	if (DEBUG) log.info("RequestV3:setHosts(): hostlist={}.", hostlist);
	for (int i = 0; i < hostlist.length(); i++) {
            // String host = hostlist.getJSONObject(i).getString("name");
            String host = hostlist.getString(i);
	    if (DEBUG) log.info("RequestV3:setHosts(): host={}.", host);
            // K0001 --> rack #1
            Integer rack = Integer.parseInt(host.substring(1,5));
            // String rack = host.substring(0,host.indexOf('-'));
            // String id = host.substring(host.indexOf('-'));

            hosts = rack2hosts.get(rack);
            if (hosts == null) {
                hosts = new ArrayList<String>();
                rack2hosts.put(rack, hosts);
            }
            hosts.add(host);
            if (DEBUG) log.info("HostList:rack:{}, add host:{}", rack, host);
        }

        for (Integer rack : rack2hosts.keySet()) {
            hosts = rack2hosts.get(rack);

            String list = "";
	    for (String s : hosts) {
                list += s + ",";
            }
            if (DEBUG) log.info("HostList:rack:{}, list:{}", rack, list);
            rack2list.put(rack, list);
        }
	return null;
    }

    private String setHosts_keep() {
	Hashtable<Integer, Hashtable> rack2hosts = 
	    new Hashtable<Integer, Hashtable>();
	Hashtable<String, String> hosts = 
	    new Hashtable<String, String>();

	if (DEBUG) log.info("RequestV3:setHosts(): hostlist={}.", hostlist);
	for (int i = 0; i < hostlist.length(); i++) {
            // String host = hostlist.getJSONObject(i).getString("name");
            String host = hostlist.getString(i);
	    if (DEBUG) log.info("RequestV3:setHosts(): host={}.", host);
            // K0001 --> rack #1
            Integer rack = Integer.parseInt(host.substring(1,5));
            // String rack = host.substring(0,host.indexOf('-'));
            // String id = host.substring(host.indexOf('-'));

            hosts = rack2hosts.get(rack);
            if (hosts == null) {
                hosts = new Hashtable<String, String>();
                rack2hosts.put(rack, hosts);
            }
            hosts.put(host, host);
            if (DEBUG) log.info("HostList:rack:{}, add host:{}", rack, host);
        }

        for (Integer rack : rack2hosts.keySet()) {
            hosts = rack2hosts.get(rack);
            String list = "";
            for (String host : hosts.keySet()) {
                list += host + ",";
            }
            if (DEBUG) log.info("HostList:rack:{}, list:{}", rack, list);
            rack2list.put(rack, list);
        }
	return null;
    }

    public String setToS() {
	return setToS(true);
    }

    public String unsetToS() {
	return setToS(false);
    }

    private String setToS(boolean isSetup) {
	if (fw) {
	    String hostlist = getFwHostList();
	    String cmd =  null;
	    if (isSetup) {
		cmd = addFwCmd;
	    } else {
		cmd = delFwCmd;
	    }

	    if (cmd != null) {
		String mpicmd = "mpirun --host " + hostlist + 
		    " --pernode " + cmd;
		if (DEBUG2) log.info("setTos(): FW: command({})", mpicmd);

		fw_direct = new SetTos(mpicmd);
		fw_direct.start();
	    }
	    if (DEBUG) log.info("RequestV3: setToS(): FW cmd={}", cmd);
	}

	if (bw) {
	    String hostlist = getBwHostList();
	    String cmd =  null;
	    if (isSetup) {
		cmd = addBwCmd;
	    } else {
		cmd = delBwCmd;
	    }

	    if (cmd != null) {
		String mpicmd = "mpirun --host " + hostlist + 
		    " --pernode " + cmd;
		if (DEBUG2) log.info("setTos(): BW: command({})", mpicmd);

		bw_direct = new SetTos(mpicmd);
		bw_direct.start();
	    }
	    if (DEBUG) log.info("RequestV3: setToS(): BW cmd={}", cmd);
	}
	return null;
    }


    public void joinToS() {
	if (SetupTOS_JOIN && fw_direct != null) {
	    while (true) {
		try {
		    fw_direct.join();
		    break;
		} catch (InterruptedException e) {}
	    }
	    String f_out = fw_direct.getOutput();
	    if (DEBUG) log.info("joinTos: fw_direct: " + f_out);
	}
	fw_direct = null;
	
	if (SetupTOS_JOIN && bw_direct != null) {
	    while (true) {
		try {
		    bw_direct.join();
		    break;
		} catch (InterruptedException e) {}
	    }
	    String r_out = bw_direct.getOutput();
	    if (DEBUG) log.info("joinTos: bw_direct: " + r_out);
	}
	bw_direct = null;
    }

    public String getFwHostList() {
	return rack2list.get(fw_srcRack);
    }

    public String getBwHostList() {
	return rack2list.get(bw_srcRack);
    }

    private String setAddFwCmd() {
	if (fw) {
	    addFwCmd = " sudo ./command/set-tos.sh -A -s " + fw_srcNW + 
		" -d " + fw_dstNW + " -u " + user + " -t " + fwTos;
	    return addFwCmd;
	}
	return null;
    }

    private String setDelFwCmd() {
	if (fw) {
	    delFwCmd = " sudo ./command/set-tos.sh -D -s " + fw_srcNW + 
		" -d " + fw_dstNW + " -u " + user + " -t " + fwTos;
	    return delFwCmd;
	}
	return null;
    }

    public String getAddFwCmd() {
	return addFwCmd;
    }

    public String getDelFwCmd() {
	return delFwCmd;
    }

    private String setAddBwCmd() {
	if (bw) {
	    addBwCmd = " sudo ./command/set-tos.sh -A -s " + bw_srcNW + 
		" -d " + bw_dstNW + " -u " + user + " -t " + bwTos;
	    return addBwCmd;
	}
	return null;
    }

    private String setDelBwCmd() {
	if (bw) {
	    delBwCmd = " sudo ./command/set-tos.sh -D -s " + bw_srcNW + 
		" -d " + bw_dstNW + " -u " + user + " -t " + bwTos;
	    return delBwCmd;
	}
	return null;
    }

    public String getAddBwCmd() {
	return addBwCmd;
    }

    public String getDelBwCmd() {
	return delBwCmd;
    }

    public boolean isFw() {
	return fw;
    }

    public boolean isBw() {
	return bw;
    }

    public int getFwSrcRack() {
	return fw_srcRack;
    }

    public int getBwSrcRack() {
	return bw_srcRack;
    }

    public int getPacketNum() 
    {
	return ncontrol;
    }

    public static boolean printPacket(int planeId, int masterId, 
				   int subMasterId, short[] param) {
				   
	int length = 0;
	if (param != null) length = param.length;
	log.info("planeId={}, masterId={}, subMasterId={}, length={}", 
		 planeId, masterId, subMasterId, param.length);
	
	int p = 0;
	log.info("packet head: {}, {}, {}, {}.,,",
		 RequestSetV3.Short2Hex(param[p+0]),
		 RequestSetV3.Short2Hex(param[p+1]),
		 RequestSetV3.Short2Hex(param[p+2]),
		 RequestSetV3.Short2Hex(param[p+3]));
	
	for (int i = 0; i < (length-HeadLength)/PacketLength; i++) {
	    p = HeadLength + i * PacketLength;
	    log.info("packet body: {}, {}, {}, {}, {}, {}, {}, {}.,,",
		     RequestSetV3.Short2Hex(param[p+0]),
		     RequestSetV3.Short2Hex(param[p+1]),
		     RequestSetV3.Short2Hex(param[p+2]),
		     RequestSetV3.Short2Hex(param[p+3]),
		     RequestSetV3.Short2Hex(param[p+4]),
		     RequestSetV3.Short2Hex(param[p+5]),
		     RequestSetV3.Short2Hex(param[p+6]),
		     RequestSetV3.Short2Hex(param[p+7]));
	} 
	return true;
    }

    private short[] setHead(short[] param) {
	if (param == null) {
	    param = new short[HeadLength];
	    param[0] = 3;
	    param[1] = 0;
	    param[2] = (short) getRequestId();
	    param[3] = 0;
	}
	param[1] += 1;
	if (DEBUG) {
	    log.info("packet head: {}, {}, {}, {}.",
		     RequestSetV3.Short2Hex(param[0]),
		     RequestSetV3.Short2Hex(param[1]),
		     RequestSetV3.Short2Hex(param[2]),
		     RequestSetV3.Short2Hex(param[3]));
	}
	return param;
    }

    public short[][][][] setParam(short[][][] params[]) {
	int subMasterId = -1;
	if (DEBUG) log.info("setParam: fwplaneId = {}", fwPlaneId);

	if (fw) {
	    try { /* set lambda of the receiver */
		int i_lsw = conf.getInt("lsw_id");
		JSONObject lsw = AppWebResource.getLswObject(i_lsw);
		masterId = lsw.getInt("MasterID");
		// subMasterId = lsw.getInt("SubMasterID");
		subMasterId = 0;
		short[] param = params[fwPlaneId][masterId][subMasterId];
		short[] p = new short[PacketLength];
		short parameterId = 0;

		param = setHead(param);
		parameterId = (short)
		    (((param.length-HeadLength)  / PacketLength) & 0xffff);
		
		p[0] = parameterId;
		p[1] = 1;	// 1: lambda, 2: optial-SW
		p[2] = (short) lsw.getInt("SlaveID");
		p[3] = 1;	// 1: setup,  2: teardown
		p[4] = (short) conf.getInt("lambda");
		p[5] = (short) conf.getInt("lsw_in");
		p[6] = 0;
		// p[7] = (short) getPID();
		p[7] = getPID();
		
		short[] newp = copyParam(param, p);
		ncontrol ++;
		params[fwPlaneId][masterId][subMasterId] = newp;
		
		if (DEBUG) printPacket(fwPlaneId, masterId, subMasterId,
					newp);
	    } catch (org.json.JSONException ex) {
		if (DEBUG2) log.info ("setParam: lsw is not exist.");
	    }
	
	    try { /* set optical core switch  */
		int i_osw = conf.getInt("osw_id");
		JSONObject osw = AppWebResource.getOswObject(i_osw);
		masterId = osw.getInt("MasterID");
		// subMasterId = osw.getInt("SubMasterID");
		subMasterId = 0;
		short[] param = params[fwPlaneId][masterId][subMasterId];
		short[] p = new short[PacketLength];
		short parameterId = 0;

		param = setHead(param);
		parameterId = (short)
		    (((param.length-HeadLength)  / PacketLength) & 0xffff);

		p[0] = parameterId;
		p[1] = 2;	// 1: lambda, 2: optial-SW
		p[2] = (short) osw.getInt("SlaveID");
		p[3] = 1;	// 1: setup,  2: teardown
		///// reverse
		// p[4] = (short) conf.getInt("osw_in");
		// p[5] = (short) conf.getInt("osw_out");
		///// reverse
		p[4] = (short) conf.getInt("osw_out");
		p[5] = (short) conf.getInt("osw_in");
		p[6] = 0;
		// [7] = (short) getPID();
		p[7] = getPID();

		short[] newp = copyParam(param, p);
		ncontrol ++;
		params[fwPlaneId][masterId][subMasterId] = newp;

		if (DEBUG) printPacket(fwPlaneId, masterId, subMasterId,
				       newp);
	    } catch (org.json.JSONException ex) {
		if (DEBUG2) log.info ("setParam: osw is not exist.");
	    }
	}

	if (bw) {
	    try { /* set lambda of the receiver */
		int i_lsw = conf.getInt("rv_lsw_id");
		JSONObject lsw = AppWebResource.getLswObject(i_lsw);
		
		masterId = lsw.getInt("MasterID");
		subMasterId = lsw.getInt("SubMasterID");
		short[] param = params[bwPlaneId][masterId][subMasterId];
		short[] p = new short[PacketLength];
		short parameterId = 0;
		
		param = setHead(param);
		parameterId = (short)
		    (((param.length-HeadLength)  / PacketLength) & 0xffff);
		
		p[0] = parameterId;
		p[1] = 1;	// 1: lambda, 2: optial-SW
		p[2] = (short) lsw.getInt("SlaveID");
		p[3] = 1;	// 1: setup,  2: teardown
		p[4] = (short) conf.getInt("lambda");
		p[5] = (short) conf.getInt("rv_lsw_in");
		p[6] = 0;
		// p[7] = (short) getPID();
		p[7] = getPID();

		short[] newp = copyParam(param, p);
		ncontrol ++;
		params[bwPlaneId][masterId][subMasterId] = newp;
		
		if (DEBUG) printPacket(fwPlaneId, masterId, subMasterId,
					newp);
	    } catch (org.json.JSONException ex) {
		if (DEBUG2) log.info ("setParam: lsw is not exist.");
	    }

	    try { /* set optical core switch  */
		int i_osw = conf.getInt("rv_osw_id");
		JSONObject osw = AppWebResource.getOswObject(i_osw);
		masterId = osw.getInt("MasterID");
		subMasterId = osw.getInt("SubMasterID");

		short[] param = params[bwPlaneId][masterId][subMasterId];
		short[] p = new short[PacketLength];
		short parameterId = 0;

		param = setHead(param);
		parameterId = (short)
		    (((param.length-HeadLength)  / PacketLength) & 0xffff);

		p[0] = parameterId;
		p[1] = 2;	// 1: lambda, 2: optial-SW
		p[2] = (short) (osw.getInt("SlaveID"));
		p[3] = 1;	// 1: setup,  2: teardown
		//// reverse
		// p[4] = (short) (conf.getInt("rv_osw_in"));
		// p[5] = (short) (conf.getInt("rv_osw_out"));
		//// reverse
		p[4] = (short) (conf.getInt("rv_osw_out"));
		p[5] = (short) (conf.getInt("rv_osw_in"));
		p[6] = 0;
		// p[7] = (short) getPID();
		p[7] = getPID();

		short[] newp = copyParam(param, p);
		params[bwPlaneId][masterId][subMasterId] = newp;
		ncontrol ++;
		if (DEBUG) printPacket(fwPlaneId, masterId, subMasterId,
				       newp);
	    } catch (org.json.JSONException ex) {
		if (DEBUG2) log.info ("setParam: osw is not exist.");
	    }
	}
	return params;
    }

    private short[] copyParam(short[] org, short[] add) {
	if (org == null) {
	    log.error("copyParam: org[] is null. internal error.");
	    return null;
	}

	short[] newp = new short[org.length + add.length];
	System.arraycopy(org, 0, newp, 0, org.length);  
	System.arraycopy(add, 0, newp, org.length, add.length);
	return newp;
    }

    protected boolean getFw() {
	return fw;
    }

    protected boolean getBw() {
	return bw;
    }

    protected int getSrcPort() {
	return srcPort;
    }

    protected int getDstPort() {
	return dstPort;
    }

    protected JSONObject getConfig() {
	return conf;
    }
}
