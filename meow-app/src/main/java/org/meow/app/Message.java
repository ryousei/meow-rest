/*
 * Copyright (c)2022 National Institute of Advanced Industrial Science 
 * and Technology (AIST). All rights reserved.
 */

package org.meow.app;

public class Message
{
    public short[] head = null;
    public short[] param = null;

    public Message (short[] head, short[] body)
    {
	this.head = head;
	this.param = body;
    }

    public String toString()
    {
	String status = "";

	status = "" + RequestSetV3.Short2Hex(param[0]);
	status += "," + RequestSetV3.Short2Hex(param[1]);
	status += "," + RequestSetV3.Short2Hex(param[2]);
	status += "," + RequestSetV3.Short2Hex(param[3]);
	status += "," + RequestSetV3.Short2Hex(param[4]);
	status += "," + RequestSetV3.Short2Hex(param[5]);
	status += "," + RequestSetV3.Short2Hex(param[6]);
	status += "," + RequestSetV3.Short2Hex(param[7]);
	status += "\n";

	return status;
    }
}
