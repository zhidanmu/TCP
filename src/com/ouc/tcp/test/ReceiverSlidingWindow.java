package com.ouc.tcp.test;

import java.util.Queue;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;

import com.ouc.tcp.client.UDT_Timer;
import com.ouc.tcp.message.TCP_PACKET;

public class ReceiverSlidingWindow extends SlidingWindow {
	
	private TCP_Receiver receiver;
	private LinkedBlockingQueue<int[]> data_deliver=new LinkedBlockingQueue<int[]>();//有序的可上交的数据
	private int min_deliver_num=20;//最小上交数量
	private volatile UDT_Timer timer = null;
	private int final_seq=99901;
	private volatile int recvCnt=0;//累积收到片段数量计数
	private int replayCntLimit=8;//反馈界限,累积数量达到反馈界限直接反馈
	
	// 构造函数
	public ReceiverSlidingWindow(TCP_Receiver r) {
		super();
		receiver=r;
	}
	
	//是否可以上交数据
	public boolean can_data_deliver(){
		if(data_deliver.size()<=0) {
			return false;
		}
		
		if(data_deliver.size()>=min_deliver_num) {
			return true;
		}
		
		if(datamap.size()<=0&&data_deliver.size()>0&&wbase>final_seq) {
			return true;
		}
		
		return false;
	}
	
	//获取可以上交的数据
	public Queue<int[]> get_data_deliver(){
		LinkedBlockingQueue<int[]> ret=new LinkedBlockingQueue<int[]>();
		for(int i=0;i<min_deliver_num;i++) {
			int[] tp=data_deliver.poll();
			if(tp==null) {
				break;
			}
			ret.add(tp);
		}
		System.out.println("deliver data(size:"+ret.size()+")");
		System.out.println();
		return ret;
	}
	
	//收包
	public boolean recvPacket(TCP_PACKET packet) {
		int seq=packet.getTcpH().getTh_seq();
		if((seq>=wbase+wsize*singleDataSize)||(seq<=wbase-wsize*singleDataSize)) {
			return false;
		}
		
		if((seq<wbase)&&(seq>wbase-wsize*singleDataSize)){
			return true;
		}
		
		if(!datamap.containsKey(seq)) {
			datamap.put(seq, packet);
			recvCnt++;
			if(seq==wbase) {
				slide();
			}
		}
		return true;
	}
	
	@Override
	void slide(){
		while(true){
			TCP_PACKET packet=datamap.get(wbase);
			if(packet==null) {
				break;
			}
			try {
				data_deliver.put(packet.getTcpS().getData().clone());
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			datamap.remove(wbase);
			wbase+=singleDataSize;
		}
		System.out.println("recver-wbase: "+get_wbase());
	}
	
	//设置ack包
	public void set_ack_packet(TCP_PACKET ackPacket) {
		int lastack=ackPacket.getTcpH().getTh_ack();
		//发送期望获取代码不可,必须发送最后一个收到的日志才会有记录
		ackPacket.getTcpH().setTh_ack(wbase-singleDataSize);//最后一个收到的seq
		ackPacket.getTcpH().setTh_sum(CheckSum.computeChkSum(ackPacket));
		//sack需要先分配空间！！！！
		int k=0;
		int lk=wbase;
		int rk=wbase;
		int mk=lastack;
		Queue<Integer> sackQueue=new LinkedBlockingQueue<Integer>();
		while(lk<mk&&rk<mk) {
			rk=lk;
			while(!datamap.containsKey(rk)&&rk<mk) {
				rk+=singleDataSize;
			}
			sackQueue.add(lk);
			sackQueue.add(rk);
			k++;
			lk=rk;
			while(datamap.containsKey(lk)&&lk<mk) {
				lk+=singleDataSize;
			}
		}
		//补充尾段
		if(rk<wbase+wsize*singleDataSize) {
			sackQueue.add(lastack+singleDataSize);
			sackQueue.add(wbase+wsize*singleDataSize);
			k++;
		}
		if(k>4)k=4;
		//分配sack空间
		ackPacket.getTcpH().setTh_sackFlag((byte) (k));
		for(int i=0;i<k;i++) {
			lk=sackQueue.poll();
			rk=sackQueue.poll();
//			System.out.println("put "+"lk/rk:"+lk+"/"+rk);
			ackPacket.getTcpH().setTh_sack_border(i, lk, rk-1);
		}
		
	}
	
	//设置计时器发包
	public void send_ack_packet(final TCP_PACKET ackPacket) {
		if(timer!=null) {
			try {
				timer.cancel();
			}catch(Exception e) {
				System.out.println(e);
			}
		}
		
		if(recvCnt>=replayCntLimit) {
			receiver.reply(ackPacket);
			recvCnt=0;
			return;
		}
		
		
		timer=new UDT_Timer();
		TimerTask task=new TimerTask(){
			@Override
			public void run() {
				receiver.reply(ackPacket);
				if(ackPacket.getTcpH().getTh_ack()>=final_seq) {
					timer.cancel();
				}
			}
		};
		timer.schedule(task,500);
		
	}
	
	
}
