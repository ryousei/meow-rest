/*
 * Copyright (c)2022 National Institute of Advanced Industrial Science 
 * and Technology (AIST). All rights reserved.
 */

package org.meow.app;

import org.json.JSONObject;
import org.json.JSONArray;

public class ResponseV3 
{
    public String s = null;
    public Integer parameterId = null;
    public int parameterNum = -1;
    public RequestV3 request = null;
    public ResponseV3 response = null;

    public int srcPort = -1;
    public int dstPort = -1;
    public Integer key = null; // RequestID == ResponseID
    public short[] rhead = null;
    public short[][] rparams = null;
    public int packetNum = -1;
    public int recvPacketNum = -1;

    public JSONObject conf = null;

    public ResponseV3(Integer key, RequestV3 request, ResponseV3 response)
    {
	this.key = key;
	this.request = request;
	this.response = response;

	if (request != null) {
	    srcPort = request.getSrcPort();
	    dstPort = request.getDstPort();
	    conf = request.getConfig();
	    packetNum = request.getPacketNum();

	    /*
	      this.planeId = planeId;
	      this.masterId = masterId;
	      this.subMasterId = subMasterId;
	      this.sparams = params;
	    */
	}
    }

    public void setResponse(int pnum, short[] head, short[][] params) 
    {
	rhead = head;
	rparams = params;
	recvPacketNum = pnum;
    }

    public boolean isReadDone()
    {
	if (recvPacketNum != -1 && rparams != null && recvPacketNum == packetNum) {
	    return true;
	}
	return false;
    }

    public void checkResponse()
    {
    }

    public String toString()
    {
	if (s != null) return s;

	s = "Version: " + Short.toUnsignedInt(rhead[0]);
	int pnum = Short.toUnsignedInt(rhead[1]);
	s += ", Num: " + pnum;
	s += ", ResponseId: " + Short.toUnsignedInt(rhead[2]);
	s += ", Reserved: " + Short.toUnsignedInt(rhead[3]);
	s += " \n ";
	for (int p = 0; p < pnum; p ++) {
	    for (int i = 0; i < 8; i ++) {
		s += ", " + i + ":0x" + Integer.toHexString(Short.toUnsignedInt(rparams[p][i]));
	    }
	    s += " \n ";
	}

	return s;
    }
}
