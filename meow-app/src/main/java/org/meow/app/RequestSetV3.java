/*
 * Copyright (c)2022 National Institute of Advanced Industrial Science 
 * and Technology (AIST). All rights reserved.
 */

package org.meow.app;

import org.json.JSONObject;
import org.json.JSONArray;
import org.yaml.snakeyaml.Yaml;

import java.util.Hashtable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestSetV3
{
    private static final Logger log = LoggerFactory.getLogger(RequestSetV3.class);

    private static final boolean DEBUG = false;
    private static final boolean DEBUG2 = true;
    private static final int PacketLength = RequestV3.PacketLength;
    private static final int HeadLength = RequestV3.HeadLength;

    static public final String FW = "FW"; // forward direction
    static public final String BW = "BW"; // backward direction
    static public final String BI = "BI"; // Both directions/bi-direction 

    static public final boolean DO_RECV_THREAD = true;
    // static public final boolean DO_RECV_THREAD = false;

    private JSONObject requests = null;
    private JSONArray reqArray = null;
    private int PORT_offset = 0;
    private int reqSize = 0;
    private short parameterId = 0;
    RequestV3[] reqs = null;
    // ResponseV3[] ress = null;

    private String dir = null;
    
    private boolean fw = false;
    private boolean bw = false;
    private int i_src = -1;
    private int i_dst = -1;
    private JSONObject conf;
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

    private int tos = -1;

    private Hashtable<Integer, String> rack2list =
	new Hashtable<Integer, String>();

    private String addFwCmd = null;
    private String addBwCmd = null;
    private String delFwCmd = null;
    private String delBwCmd = null;
    private int N_Plane = 0;
    private int N_Master = 0;
    private int N_SubMaster = 0;
    private short[][][] params[] = null;
    private boolean isSetParams = false;
    static private AppControllerV3[][][] controls = null;

    static String Short2Hex(short s) {
	return Integer.toHexString(Short.toUnsignedInt(s));
    }

    public RequestSetV3 (JSONObject requests, int PORT_offset, 
			 int N_Plane, int N_Master, int N_SubMaster,
			 AppControllerV3[][][] controls) throws Exception {
	this.requests = requests;
	this.PORT_offset = PORT_offset;
	this.N_Plane = N_Plane;
	this.N_Master = N_Master;
	this.N_SubMaster = N_SubMaster;
	this.controls = controls;

	reqArray = requests.getJSONArray("requests");
	reqSize = reqArray.length();
	reqs = new RequestV3[reqSize];
	// ress = new ResponseV3[reqSize];

	for (int r = 0; r < reqSize; r ++) {
	    reqs[r] = new RequestV3(reqArray.getJSONObject(r), PORT_offset,
				    N_Plane, N_Master, parameterId);
	    // ress[r] =reqs[r].getReponse();
	    parameterId ++;
	}
    }

    public boolean markPath() {
	for (int r = 0; r < reqSize; r ++) {
	    boolean f = reqs[r].markPath();
	    if (! f) {
		for (int rr = 0; rr < r; rr ++) {
		    reqs[r].unmarkPath();
		}
		return false;
	    }
	}
	return true;
    }

    public void unmarkPath() {
	for (int r = 0; r < reqSize; r ++) {
	    reqs[r].unmarkPath();
	}
    }
	
    public void setParams() {
	if (isSetParams) return;

	isSetParams = true;
	params = new short[N_Plane][N_Master][N_SubMaster][];
	
	for (int r = 0; r < reqSize; r ++) {
	    params = reqs[r].setParam(params);
	}
    }

    public void setSW() throws Exception {
	if (! isSetParams) {
	    log.info("RequestSetV3: setSW(): not set parameters.");
	    return;
	}
	for (int p = 0; p < N_Plane; p++) {
	    for (int m = 0; m < N_Master; m++) {
		for (int s = 0; s < N_SubMaster; s++) {
		    short[] param = params[p][m][s];
		    if (DEBUG2) log.info("RequestSetV3: setSW():[{}][{}][{}]={}.", p, m, s, param);
		    if (param == null) continue;

		    setSW(p, m, s, param);
		}
	    }
        }
    }

    public void unsetSW() throws Exception {
	if (! isSetParams) {
	    log.info("RequestSetV3: setSW(): not set parameters.");
	    return;
	}
	for (int p = 0; p < N_Plane; p++) {
	    for (int m = 0; m < N_Master; m++) {
		for (int s = 0; s < N_SubMaster; s++) {
		    short[] param = params[p][m][s];
		    if (DEBUG2) log.info("RequestSetV3: unsetSW():[{}][{}][{}]={}.", p, m, s, param);
		    if (param == null) continue;

		    unsetSW(p, m, s, param);
		}
	    }
        }
    }

    private boolean setSW(int planeId, int masterId, int subMasterId,
			  short[] param) {
	try {
	    if (DEBUG) RequestV3.printPacket(planeId, masterId, subMasterId, param);

	    Integer key = new Integer(Short.toUnsignedInt(param[2]));
	    int length = param.length;
	    for (int i = 0; i < param[1]; i++) {
		int pos0 = HeadLength + (i * PacketLength);
		int pos3 = pos0 + 3;
		if (DEBUG) log.info("setSW: key={}, pos0={}, pos3={}", key, pos0, pos3);

		param[pos0] = (short) i; // ParameterID
		param[pos3] = (short) 1; // setup command
	    }
	    if (DEBUG) log.info("setSW: param.length={}", length);

	    AppControllerV3 control = controls[planeId][masterId][subMasterId];
	    synchronized (control) {
		ResponseSetV3 res = new ResponseSetV3(param, this, planeId, 
						      masterId, subMasterId);
		control.sendShort(param);
		if (DEBUG) log.info("setSW: get res={}", res);
	    }
	} catch (Exception ex) {
	    ex2log(ex);
	}
	return true;
    }

    private boolean unsetSW(int planeId, int masterId, int subMasterId,
			  short[] param) throws Exception {
	try {
	    if (DEBUG) RequestV3.printPacket(planeId, masterId, subMasterId, param);

	    Integer key = new Integer(Short.toUnsignedInt(param[2]));
	    int length = param.length;
	    for (int i = 0; i < param[1]; i++) {
		int pos0 = HeadLength + (i * PacketLength);
		int pos3 = pos0  + 3;
		if (DEBUG) log.info("setSW: key={}, pos0={}, pos3={}", key, pos0, pos3);

		param[pos0] = (short) i; // ParameterID
		param[pos3] = (short) 2; // teardown command
	    }
	    
	    AppControllerV3 control  = controls[planeId][masterId][subMasterId];
	    if (DEBUG) log.info("setSW: param.length={}", length);

	    synchronized (control) {
		ResponseSetV3 res = new ResponseSetV3(param, this, planeId, 
						      masterId, subMasterId);
		control.sendShort(param);
		if (DEBUG) log.info("unsetSW: get res={}", res);
	    }
	} catch (Exception ex) {
	    ex2log(ex);
	}
	return true;
    }

    public void joinToS() {
	for (int r = 0; r < reqSize; r ++) {
	    reqs[r].joinToS();
	}
    }

    public void setToS() {
	for (int r = 0; r < reqSize; r ++) {
	    reqs[r].setToS();
	}
    }

    public void unsetToS() {
	for (int r = 0; r < reqSize; r ++) {
	    reqs[r].unsetToS();
	}
    }

    public void ex2log(Exception ex)
    {
	/*
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
	*/

    }
}
