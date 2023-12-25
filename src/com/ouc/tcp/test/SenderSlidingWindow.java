package com.ouc.tcp.test;

import java.util.Queue;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;

import com.ouc.tcp.client.Client;
import com.ouc.tcp.client.UDT_Timer;
import com.ouc.tcp.message.TCP_PACKET;

public class SenderSlidingWindow extends SlidingWindow{
	
	private volatile UDT_Timer timer = null;
	private TCP_Sender sender;
	private volatile int wlast=-1;
	
	//构造函数
	public SenderSlidingWindow(TCP_Sender s) {
		super();
		sender=s;
	}
	
	//计时器相关
	//重启计时器
	void retimer() {
		if(timer!=null) {
			try {
				timer.cancel();
			}catch(Exception e) {
				System.out.println(e);
			}
		}
		timer=new UDT_Timer();
		TimerTask task=new TimerTask(){
			@Override
			public void run() {
				int nidx=wbase;
				for(;nidx<=wbase+wsize*singleDataSize;nidx+=singleDataSize) {
					TCP_PACKET packet=datamap.get(nidx);
					if(packet!=null) {
						sender.udt_send(packet);
					}
				}
				
			}
		};
		timer.schedule(task, 500,500);
	}
	
	
	//放入数据
	public boolean put_packet(TCP_PACKET packet) {
		if(datamap.size()>wsize) {
			return false;
		}
		int seq=packet.getTcpH().getTh_seq();
		if(seq>=wbase+wsize*singleDataSize) {
			return false;
		}
		
		
		datamap.put(seq, packet);
		wlast=wlast>seq?wlast:seq;
		return true;
	}
	
	//处理ack
	public boolean recvACK(int ack){
		if(ack>=wbase+wsize*singleDataSize) {
			return false;
		}
		if(ack<wbase) {
			return true;
		}
//		System.out.println("handleAck: "+ack);
//		System.out.println("windowsize: "+get_datamap_size());
		datamap.remove(ack);
		retimer();
		if(ack==wbase) {
			slide();
		}
		return true;
	}
	
	@Override
	public void slide() {
		while(wbase<=wlast&&!datamap.contains(wbase)) {
			wbase+=singleDataSize;
			System.out.println("sender-wbase: "+get_wbase());
		}
	}
	
	
	@Override
	public boolean isFull(){
		if(datamap.size()>=wsize) {
			return true;
		}
		
		if(wlast>=wbase+(wsize-1)*singleDataSize) {
			return true;
		}
		
		return false;
	}
}
