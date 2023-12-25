package com.ouc.tcp.test;

import java.util.concurrent.ConcurrentHashMap;

import com.ouc.tcp.message.TCP_PACKET;

public class SlidingWindow {
	
	volatile int wbase=1;//窗口首部序号
	volatile int wsize=8;//窗口大小
	ConcurrentHashMap<Integer,TCP_PACKET> datamap=new ConcurrentHashMap<Integer,TCP_PACKET>(); 
	int singleDataSize=100;//单组数据大小
	
	//构造函数
	public SlidingWindow(){
		
	}
	
	//getter&setter
	public int get_wbase() {
		return wbase;
	}
	public void set_wbase(int v) {
		wbase=v;
	}
	public int get_wsize(){
		return wsize;
	}
	public void set_wsize(int v) {
		wsize=v;
	}
	public int get_datamap_size() {
		return datamap.size();
	}
	
	//是否空或满
	public boolean isEmpty() {
		return datamap.isEmpty();
	}
	public boolean isFull(){
		return (datamap.size()>=wsize);
	}
	
	//滑动
	 void slide() {
		
	}
	
	
}
