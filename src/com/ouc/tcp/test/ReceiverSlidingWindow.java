package com.ouc.tcp.test;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import com.ouc.tcp.message.TCP_PACKET;

public class ReceiverSlidingWindow extends SlidingWindow {
	
	private LinkedBlockingQueue<int[]> data_deliver=new LinkedBlockingQueue<int[]>();//有序的可上交的数据
	private int min_deliver_num=20;//最小上交数量
	
	
	// 构造函数
	public ReceiverSlidingWindow() {
		super();
	}
	
	//是否可以上交数据
	public boolean can_data_deliver(){
		if(data_deliver.size()<=0) {
			return false;
		}
		
		if(data_deliver.size()>=min_deliver_num) {
			return true;
		}
		
		if(datamap.size()<=0&&data_deliver.size()>0) {
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
		System.out.println("deliver data");
		return ret;
	}
	
	//收包
	public boolean recvPacket(TCP_PACKET packet) {
		int seq=packet.getTcpH().getTh_seq();
		if(seq>=wbase+wsize*singleDataSize) {
			return false;
		}
		
		if(seq<wbase){
			return true;
		}
		
		if(!datamap.containsKey(seq)) {
			datamap.put(seq, packet);
			slide();
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
				data_deliver.put(packet.getTcpS().getData());
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			//wbase+=packet.getTcpS().getData().length;
			wbase+=singleDataSize;
			System.out.println("recver-wbase: "+get_wbase());
		}
	}

}
