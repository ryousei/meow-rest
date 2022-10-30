/*
 * Copyright (c)2022 National Institute of Advanced Industrial Science 
 * and Technology (AIST). All rights reserved.
 */

package org.meow.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;

public class SpawnExpect extends Thread
{
    static private final Logger log = LoggerFactory.getLogger(SpawnExpect.class);
    static public final boolean DEBUG = false;

    String host = null;
    String cmd = null;
    Process p = null;
    String output = "";
    boolean stop = true;

    String command = "";
    // This indicates the location of the scripts in remote host.
    String dir = "expect/";
    // Set remote host and  run the script.
    String ssh = "ssh nedo05 expect ";

    String cmdUp = ssh + dir + "up.cntl | grep TIME";
    String cmdDown = ssh + dir + "down.cntl | grep TIME";

    InputStream is = null;
    BufferedReader br = null;

    public SpawnExpect()
    {
    }

    public void setUp()
    {
	command = cmdUp;
    }

    public void setDown()
    {
	command = cmdDown;
    }

    public void run() 
    {
        Runtime runtime = Runtime.getRuntime();
	if (DEBUG) log.info("SpawnExpect: command: {}.", command);

	try {
	    stop = false;
	    p = runtime.exec(command);
	} catch (IOException e) {
	    e.printStackTrace();
	    output = "" + e;
	    stop = true;
	    return;
	}

	try {
	    p.waitFor();
	} catch (InterruptedException e) {}

        is = p.getInputStream();
	br = new BufferedReader(new InputStreamReader(is));

	String line = null;
	try {
	    output = "";
	    while (true) {
		line = br.readLine();
		if (line == null) break;
		// System.out.println("output += " + line);
		output += line + "\n";
	    }
	    stop = true;
	} catch (IOException e) {
	    e.printStackTrace();
	    output += "\n" + e;
	}
	if (DEBUG) log.info("SpawnExpect: Time: {}.", output);
    }

    public String getOutput()
    {
	return output;
    }

    public boolean isStop()
    {
	return stop;
    }

    public static void main(String[] args) 
    {
	SpawnExpect se = new SpawnExpect();
	try {
	    se.setUp();
	    se.start();
	    se.join();
	    System.err.println("UP OUTPUT: " + se.getOutput());
	    Thread.sleep(2);
	} catch (InterruptedException e) {
	    e.printStackTrace();
	}

	se = new SpawnExpect();
	try {
	    se.setDown();
	    se.start();
	    se.join();
	    System.err.println("DOWN OUTPUT: " + se.getOutput());
	} catch (InterruptedException e) {
	    e.printStackTrace();
	}
    }
}
