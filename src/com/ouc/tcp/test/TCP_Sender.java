/***************************2.1: ACK/NACK
**************************** Feng Hong; 2015-12-09*/

package com.ouc.tcp.test;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import com.ouc.tcp.client.TCP_Sender_ADT;
import com.ouc.tcp.client.UDT_RetransTask;
import com.ouc.tcp.client.UDT_Timer;
import com.ouc.tcp.message.*;
import com.ouc.tcp.tool.TCP_TOOL;

public class TCP_Sender extends TCP_Sender_ADT {

	private TCP_PACKET tcpPack; // 待发送的TCP数据报
	private volatile int flag = 1;

//	private UDT_Timer timer = null;
//	private UDT_RetransTask reTrans = null;

	private SenderSlidingWindow sslwindow = new SenderSlidingWindow(this);

	/* 构造函数 */
	public TCP_Sender() {
		super(); // 调用超类构造函数
		super.initTCP_Sender(this); // 初始化TCP发送端
		
		//设置窗口初始大小
		sslwindow.set_wsize(1);
	
	}

	@Override
	// 可靠发送（应用层调用）：封装应用层数据，产生TCP数据报；需要修改
	public void rdt_send(int dataIndex, int[] appData) {

		// 生成TCP数据报（设置序号和数据字段/校验和),注意打包的顺序
		tcpH.setTh_seq(dataIndex * appData.length + 1);// 包序号设置为字节流号：
		tcpS.setData(appData);
		tcpPack = new TCP_PACKET(tcpH, tcpS, destinAddr);
		// 更新带有checksum的TCP 报文头
		tcpH.setTh_sum(CheckSum.computeChkSum(tcpPack));
		tcpPack.setTcpH(tcpH);

		if (sslwindow.isFull()) {
			flag = 0;
		//	System.out.println();
			System.out.println("sslwindow full");
		//	System.out.println();
		}else {
		//	System.out.println();
		//	System.out.println("sslwindow size:"+sslwindow.get_datamap_size());
		//	System.out.println();
		}
		while (flag == 0);

		try {
			sslwindow.put_packet(tcpPack.clone());
		} catch (CloneNotSupportedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// 发送TCP数据报
		udt_send(tcpPack);
		// flag = 0;

		// 设置timer
		// timer=new UDT_Timer();
		// reTrans=new UDT_RetransTask(client,tcpPack);
		// timer.schedule(reTrans,3000,3000);;

		// 等待ACK报文
		// waitACK();
		// while (flag==0);
	}

	@Override
	// 不可靠发送：将打包好的TCP数据报通过不可靠传输信道发送；仅需修改错误标志
	public void udt_send(TCP_PACKET stcpPack) {
		// 设置错误控制标志
		/*
		 * 0.信道无差错 1.只出错 2.只丢包 3.只延迟 4.出错 / 丢包 5.出错 / 延迟 6.丢包 / 延迟 7.出错 / 丢包 / 延迟
		 */
		tcpH.setTh_eflag((byte) 7);
		// System.out.println("to send: "+stcpPack.getTcpH().getTh_seq());
		// 发送数据报
		client.send(stcpPack);
	}
	
	private LinkedBlockingQueue<TCP_PACKET> recvQueue=new LinkedBlockingQueue<TCP_PACKET>();//收到的包

	@Override
	// 需要修改
	public void waitACK() {
		// 循环检查ackQueue
		// 循环检查确认号对列中是否有新收到的ACK
		/*
		 * while(true) { if(!ackQueue.isEmpty()){ int currentAck=ackQueue.poll(); //
		 * System.out.println("CurrentAck: "+currentAck); if (currentAck ==
		 * tcpPack.getTcpH().getTh_seq()){
		 * System.out.println("Clear: "+tcpPack.getTcpH().getTh_seq()); flag = 1;
		 * timer.cancel(); break; }else{
		 * System.out.println("Retransmit: "+tcpPack.getTcpH().getTh_seq());
		 * udt_send(tcpPack); flag = 0; } } }
		 */
	/*
		while(!ackQueue.isEmpty()) {
			int currentAck = ackQueue.poll();
			System.out.println("CurrentAck: "+currentAck);
			sslwindow.recvACK(currentAck);
			if(!sslwindow.isFull()) {
				flag=1;
			}else {
				flag=0;
			}
			
		}
	*/
		while(!recvQueue.isEmpty()) {
			TCP_PACKET currentRecv = recvQueue.poll();
			//sslwindow.recvACK(currentAck);
			if(currentRecv!=null) {
				sslwindow.recvACKPacket(currentRecv);
			}
			if(!sslwindow.isFull()) {
				flag=1;
			}else {
				flag=0;
			}	
		}
		
	}

	@Override
	// 接收到ACK报文：检查校验和，将确认号插入ack队列;NACK的确认号为－1；不需要修改
	public void recv(TCP_PACKET recvPack) {
		if (CheckSum.computeChkSum(recvPack) == recvPack.getTcpH().getTh_sum()) {
			// 进行校验
			System.out.println("Receive ACK Number： " + recvPack.getTcpH().getTh_ack());
			//ackQueue.add(recvPack.getTcpH().getTh_ack());
			try {
				recvQueue.put(recvPack.clone());
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
			System.out.println();
		} else {
			System.out.println("Receive ACK ERROR:" + recvPack.getTcpH().getTh_ack());
		//	ackQueue.add(-1);
			System.out.println();
		}
		// 处理ACK报文
		waitACK();

	}

}
