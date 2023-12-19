package com.ouc.tcp.test;

import java.util.zip.CRC32;

import com.ouc.tcp.message.TCP_HEADER;
import com.ouc.tcp.message.TCP_PACKET;
import com.ouc.tcp.message.TCP_SEGMENT;

public class CheckSum {
	
	/*计算TCP报文段校验和：只需校验TCP首部中的seq、ack和sum，以及TCP数据字段*/
	public static short computeChkSum(TCP_PACKET tcpPack) {
		int checkSum = 0;
		
		TCP_HEADER header=tcpPack.getTcpH();
		TCP_SEGMENT packet=tcpPack.getTcpS();
		
		int seq=header.getTh_seq();
		int ack=header.getTh_ack();
		int[] data=packet.getData();
		
		checkSum+=(seq>>>16)+(seq&0x0000ffff);
		if(checkSum>0x0000ffff) {
			checkSum=(checkSum>>>16)+(checkSum&0x0000ffff);
		}
		checkSum+=(ack>>>16)+(ack&0x0000ffff);
		if(checkSum>0x0000ffff) {
			checkSum=(checkSum>>>16)+(checkSum&0x0000ffff);
		}
		
		for(int i=0;i<data.length;i++) {
			checkSum+=(data[i]>>>16)+(data[i]&0x0000ffff);
			if(checkSum>0x0000ffff) {
				checkSum=(checkSum>>>16)+(checkSum&0x0000ffff);
			}
		}
		
		
		
		return (short) checkSum;
	}
	
}
