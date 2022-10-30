/*
 * Copyright (c)2022 National Institute of Advanced Industrial Science 
 * and Technology (AIST). All rights reserved.
 */

package org.meow.app;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;

public class SetTos extends Thread
{
    String[] cmds = null;
    String cmd = null;
    Process p = null;
    String output = "";

    InputStream is = null;
    BufferedReader br = null;

    public SetTos(String[] cmds)
    {
	this.cmds = cmds;
    }

    public SetTos(String cmd)
    {
	this.cmd = cmd;
    }

    public void run() 
    {
        Runtime runtime = Runtime.getRuntime();

	try {
	    p = runtime.exec(cmd);
	} catch (IOException e) {
	    e.printStackTrace();
	}
	try {
	    p.waitFor();
	} catch (InterruptedException e) {}

        is = p.getInputStream();
	br = new BufferedReader(new InputStreamReader(is));

	String line = null;
	try {
	    while (true) {
		line = br.readLine();
		if (line == null) break;
		output += line + "\n";
		// System.out.println("output=" + output);
	    }
	} catch (IOException e) {
	    e.printStackTrace();
	}
    }

    public String getOutput()
    {
	return output;
    }

    public static void main(String[] arg)
    {
	SetTos tos1, tos2;
	String o1, o2;

	String[] cmd1 = {"ssh", "k0001-001", 
			 "sudo ./command/set-tos.sh -D -s 10.1.101.0/24 -d 10.1.102.0/24 -u meowadmin -t 0x01"};
	String[] cmd2 = {"ssh", "k0002-001", 
			 "sudo ./command/set-tos.sh -D -s 10.1.102.0/24 -d 10.1.101.0/24 -u meowadmin -t 0x01"};

	/*
	  String[] cmd1 = {"ssh", "k0001-001", "sudo ./command/set-tos.sh -D -d 10.1.102.0/24 -u meowadmin -t 0x01"};
	  String[] cmd2 = {"ssh", "k0002-001", "sudo ./command/set-tos.sh -D -d 10.1.101.0/24 -u meowadmin -t 0x01"};
	  String[] cmd1 = {"ssh", "k0001-001", "hostname"};
	  String[] cmd2 = {"ssh", "k0002-001", "hostname"};
	*/

	tos1 = new SetTos(cmd1);
	tos1.start();

	tos2 = new SetTos(cmd2);
	tos2.start();

	while (true) {
	    try {
		tos1.join();
		break;
	    } catch (InterruptedException e) {}
	}
	o1 = tos1.getOutput();

	while (true) {
	    try {
		tos2.join();
		break;
	    } catch (InterruptedException e) {}
	}
	o2 = tos2.getOutput();

	System.out.println("o1=\n" + o1);
	System.out.println("o2=\n" + o2);
    }
}
