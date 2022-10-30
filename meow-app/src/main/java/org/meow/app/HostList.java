/*
 * Copyright (c)2022 National Institute of Advanced Industrial Science 
 * and Technology (AIST). All rights reserved.
 */

package org.meow.app;

import org.json.JSONObject;
import org.json.JSONArray;
import org.yaml.snakeyaml.Yaml;

import java.util.Hashtable;
import java.util.Enumeration;
import java.util.List;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HostList
{
    private static final Logger log = LoggerFactory.getLogger(HostList.class);
    public static final boolean DEBUG = false;

    private Hashtable<Integer, Hashtable> rack2hosts = 
	new Hashtable<Integer, Hashtable>();
    private Hashtable<String, String> hosts = 
	new Hashtable<String, String>();
    private Hashtable<Integer, String> rack2list =
	new Hashtable<Integer, String>();

    JSONArray hostlist = null;

    public HostList (JSONArray hostlist) {
	this.hostlist = hostlist;
	
        for (int i = 0; i < hostlist.length(); i++) {
            String host = hostlist.getJSONObject(i).getString("name");
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
            // String s = rack + ":";
            String list = "";
            for (String host : hosts.keySet()) {
                list += host + ",";
            }
	    if (DEBUG) log.info("HostList:rack:{}, list:{}", rack, list);
	    rack2list.put(rack, list);
        }
    }

    public String getList(int rack) {
	return rack2list.get(rack);
    }
}
