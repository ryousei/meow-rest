/*
 * Copyright (c)2022 National Institute of Advanced Industrial Science 
 * and Technology (AIST). All rights reserved.
 */

package org.meow.app;

import org.json.JSONObject;
import org.json.JSONArray;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Hashtable;

public class ResponseSetV3 
{
    public static boolean DEBUG = false;
    private static final Logger log = LoggerFactory.getLogger(ResponseSetV3.class);
    public static Hashtable responses = new Hashtable<Integer, ResponseSetV3>();

    public Integer parameterId = null;
    public int parameterNum = -1;
    public RequestV3 request = null;
    public ResponseV3 response = null;

    public int srcPort = -1;
    public int dstPort = -1;
    public Integer key = null; // RequestID == ResponseID

    public short[] rhead = null;
    public short[][] rparams = null;
    public short[] sparam = null;

    public JSONObject conf = null;

    public RequestSetV3 requests = null;
    public int planeId = -1;
    public int masterId = -1;
    public int subMasterId = -1;

    public static final int PacketLength = RequestV3.PacketLength;
    public static final int HeadLength = RequestV3.HeadLength;

    public int recvPacketNum = -1;
    public int sendPacketNum = -1;
    public String s = null;

    public ResponseSetV3()
    {
    }

    public ResponseSetV3(short[] param, RequestSetV3 requests, int planeId, 
			 int masterId, int subMasterId)
    {
	sparam = param;
	this.requests = requests;
	this.planeId = planeId;
	this.masterId = masterId;
	this.subMasterId = subMasterId;
	if (param != null) {
	    sendPacketNum = (param.length - HeadLength) / PacketLength;
	    recvPacketNum = new Integer(Short.toUnsignedInt(param[1]));
	    key = new Integer(Short.toUnsignedInt(param[2]));
	    
	    if (sendPacketNum != recvPacketNum) 
		log.error("sendPacketNum={}, recvPacketNum={}.",
			  sendPacketNum, recvPacketNum);

	    if (DEBUG) log.info("add responses key={}, o={}", key, this);
	    responses.put(key, this);
	} else {
	    log.error("ResponseSetV3: param == null (#1).");
	}
    }

    public void setResponse(short[] head, short[][] param)
    {
	if (head != null) rhead = head;
	if (param != null) rparams = param;
    }

    public boolean isOK(int i)
    {
	int s = Short.toUnsignedInt(rparams[i][1]);
	switch (s) {
	case 0:
	    return true;
	default:
	    return false;
	}
    }

    public boolean checkRecvDone()
    {
	if (rhead == null) return false;
	int pnum = Short.toUnsignedInt(rhead[1]);
	if (rparams == null) return false;

	for (int i = 0; i < pnum; i++) {
	    int s = Short.toUnsignedInt(rparams[i][0]);
	    if (s != i) {
		log.info("checkRecvDone: responseID is invalid order. paket={}, id={}",
			 i, s);
		return false;
	    }
	}
	return true;
    }

    public String toString()
    {
	if (sparam == null) return "sparam is null.";
	if (rhead == null) return "rhead is null.";

	s = "Version: " + Short.toUnsignedInt(rhead[0]);
	int pnum = Short.toUnsignedInt(rhead[1]);
	s += ", Num: " + pnum;
	s += ", ResponseId: " + Short.toUnsignedInt(rhead[2]);
	s += ", Reserved: " + Short.toUnsignedInt(rhead[3]);
	s += " \n ";
	for (int p = 0; p < pnum; p ++) {
	    for (int i = 0; i < PacketLength; i ++) {
		s += ", " + i + ":0x" + Integer.toHexString(Short.toUnsignedInt(rparams[p][i]));
	    }
	    s += " \n ";
	}

	return s;
    }
}
