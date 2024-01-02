package com.ouc.tcp.test;

import java.util.Queue;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;

import com.ouc.tcp.client.Client;
import com.ouc.tcp.client.UDT_Timer;
import com.ouc.tcp.message.TCP_PACKET;

public class SenderSlidingWindow extends SlidingWindow {

	private volatile UDT_Timer timer = null;
	private TCP_Sender sender;
	private volatile int wlast = -1;// 当前最后发送的seq
	private volatile int dupack = 0;// 重复收到的ack
	private int final_seq = 99901;// 最后片段的seq
	private volatile int resendEndIndex = -1;// 重发结束seq

	private volatile int ssthresh = 16;

	// 构造函数
	public SenderSlidingWindow(TCP_Sender s) {
		super();
		sender = s;
	}

	// 计时器相关
	// 重启计时器
	void retimer() {
		if (timer != null) {
			try {
				timer.cancel();
			} catch (Exception e) {
				System.out.println(e);
			}
		}

		if (wbase > final_seq) {
			// 最后一段已经发送
			return;
		}

		timer = new UDT_Timer();
		TimerTask task = new TimerTask() {
			@Override
			public void run() {
				// 超时，慢开始
				ssthresh = wsize / 2;
				wsize = 1;
				dupack = 0;
				System.out.println("slow start");
				System.out.println("cwnd:" + wsize);
				System.out.println("wbase:" + get_wbase());
				// 重发后置防止增加拥堵
				resendEndIndex=wlast;
				resendAll();
			}
		};
		timer.schedule(task, 3000, 3000);
	}

	// 重发窗口内数据
	public void resendAll() {
		int nidx = wbase;
		System.out.println("resend from seq:" + nidx + " to "+ resendEndIndex);
		System.out.println();
		for (; nidx <= wbase + wsize * singleDataSize && nidx<=resendEndIndex; nidx += singleDataSize) {
			TCP_PACKET packet = datamap.get(nidx);
			if (packet != null) {
				sender.udt_send(packet);
			}
		}
		if(nidx>resendEndIndex) {
			resendEndIndex=-1;
		}
	}

	// 放入数据
	public boolean put_packet(TCP_PACKET packet) {
		int seq = packet.getTcpH().getTh_seq();
		datamap.put(seq, packet);
		wlast = wlast > seq ? wlast : seq;
		return true;
	}

	// 处理ack
	@Deprecated
	public boolean recvACK(int ack) {
		if (ack > wlast) {
			return false;
		}
		if (ack < wbase) {
			return true;
		}
//		System.out.println("handleAck: "+ack);
//		System.out.println("windowsize: "+get_datamap_size());
		datamap.remove(ack);
		retimer();
		if (ack == wbase) {
			slide();
		} else {
			dupack++;
		}
		return true;
	}

	// 处理ack包
	public boolean recvACKPacket(TCP_PACKET ackPacket) {
		int ack = ackPacket.getTcpH().getTh_ack();

		int[] donotRecv = ackPacket.getTcpH().getTh_sack_borders();

		/*
		 * System.out.println("donolen:"+donotRecv.length); for(int
		 * j=0;j<donotRecv.length;j++) {
		 * System.out.println("donorecv"+j+":"+donotRecv[j]); }
		 */

		if (ack < wbase - singleDataSize) {
			return false;
		}

		for (int i = 1; i * 2 < donotRecv.length; i++) {
			int lr = donotRecv[2 * i - 1] + 1;
			int rr = donotRecv[2 * i] - 1;

//			System.out.println("lr/rr:"+lr+"/"+rr);
			int nr = lr;
			while (nr < rr) {
				datamap.remove(nr);
				nr += singleDataSize;
//				System.out.println("remove:"+nr);
			}

		}

		System.out.println("handle ack:" + ack);
		// 因为期望收到不会在log里记录,仅能处理前一个记录
		if (ack == wbase - singleDataSize) {
			//dupack++;
			if (dupack >= 3) {
				// 快恢复
				dupack = 0;
				ssthresh = wsize;
				ssthresh /= 2;
				wsize = ssthresh;
				System.out.println("fast recovery");
				System.out.println("cwnd:" + wsize);
				System.out.println();
				retimer();
				resendEndIndex=wlast;
				resendAll();
			}
		} else {
			dupack = 0;
			int beforesize = datamap.size();
			slideTo(ack + singleDataSize);
			int aftersize = datamap.size();
			retimer();
			// 拥塞控制
			if (wsize < ssthresh) {
				wsize += beforesize - aftersize;
			} else {
				wsize++;
			}
			System.out.println("cwnd:" + wsize);
		}
		if(resendEndIndex!=-1) {
			resendAll();
		}
		
		return true;
	}

	@Override
	@Deprecated
	public void slide() {
		dupack = 0;
		while (wbase <= wlast && !datamap.contains(wbase)) {
			wbase += singleDataSize;
		}
		System.out.println("sender-wbase: " + get_wbase());
	}

	public void slideTo(int newack) {
		while (wbase < newack) {
			datamap.remove(wbase);
			wbase += singleDataSize;
			System.out.println("sender-wbase: " + get_wbase());
		}
	}

	// override是否判满
	@Override
	public boolean isFull() {
		if (wlast >= wbase + (wsize - 1) * singleDataSize) {
			return true;
		}
		if(resendEndIndex>0) {
			return true;//重发未完成
		}

		return false;
	}

}
