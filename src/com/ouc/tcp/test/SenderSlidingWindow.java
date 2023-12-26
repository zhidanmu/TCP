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
	private volatile int wlast=-1;//当前最后发送的seq
	private volatile int dupack=0;//重复收到的ack
	private int final_seq=99901;//最后片段的seq
	
	private volatile int ssthresh=16;
	
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
		
		if(wbase>final_seq) {
			//最后一段已经发送
			return;
		}
			
		timer=new UDT_Timer();
		TimerTask task=new TimerTask(){
			@Override
			public void run() {
				resendAll();
				//超时，慢开始
				ssthresh=wsize/2;
				wsize=1;
				dupack=0;
				System.out.println("slow start");
				System.out.println("cwnd:"+wsize);
			}
		};
		timer.schedule(task,3000,3000);
	}
	
	//重发窗口内数据
	public void resendAll() {
		int nidx=wbase;
		for(;nidx<=wbase+wsize*singleDataSize;nidx+=singleDataSize) {
			TCP_PACKET packet=datamap.get(nidx);
			if(packet!=null) {
				sender.udt_send(packet);
			}
		}
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
		}else{
			dupack++;
		}
		return true;
	}
	
	//处理ack包
	public boolean recvACKPacket(TCP_PACKET ackPacket){
		int ack=ackPacket.getTcpH().getTh_ack();
		
		int[] donotRecv=ackPacket.getTcpH().getTh_sack_borders();

		/*
		System.out.println("donolen:"+donotRecv.length);
		for(int j=0;j<donotRecv.length;j++) {
			System.out.println("donorecv"+j+":"+donotRecv[j]);
		}
		*/
		
		if(ack<wbase-singleDataSize) {
			return true;
		}
		
		for(int i=1;i*2<donotRecv.length;i++) {
			int lr=donotRecv[2*i-1]+1;
			int rr=donotRecv[2*i]-1;
			
//			System.out.println("lr/rr:"+lr+"/"+rr);
			int nr=lr;
			while(nr<rr) {
				datamap.remove(nr);
				nr+=singleDataSize;
//				System.out.println("remove:"+nr);
			}
			
		}
		
		//因为期望收到不会在log里记录,仅能处理前一个记录
		if(ack==wbase-singleDataSize) {
			dupack++;
			if(dupack>=3) {
				//快恢复
				dupack=0;
				resendAll();	
				retimer();
				ssthresh=wsize;
				ssthresh/=2;
				wsize=ssthresh;
			}else {
				//拥塞控制
				if(wsize<ssthresh) {
					wsize*=2;
				}else {
					wsize++;
				}
			}
			
		}else{
			dupack=0;
			slideTo(ack+singleDataSize);
			retimer();
			//拥塞控制
			if(wsize<ssthresh) {
				wsize*=2;
			}else {
				wsize++;
			}
		}
		System.out.println("cwnd:"+wsize);
		return true;
	}
	
	
	@Override
	public void slide() {
		dupack=0;
		while(wbase<=wlast&&!datamap.contains(wbase)) {
			wbase+=singleDataSize;
		}
		System.out.println("sender-wbase: "+get_wbase());
	}
	
	public void slideTo(int newack) {
		while(wbase<newack) {
			datamap.remove(wbase);
			wbase+=singleDataSize;
//			System.out.println("sender-wbase: "+get_wbase());
		}
	}
	
	
	//override是否判满
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
