package net.floodlightcontroller.trafficmonitor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;

import net.floodlightcontroller.core.types.NodePortTuple;

import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class TrafficAnalyzer {
	private static final Logger logger = LoggerFactory.getLogger(TrafficAnalyzer.class);

	public static void Analysis(HashMap<NodePortTuple, SwitchPortStatistics> portStats, HashMap<NodePortTuple, SwitchPortStatistics> abnormalTraffic, Policy policy){
		U64 portSpeed = U64.ZERO;
		U64 rxSpeed = U64.ZERO;
		U64 txSpeed = U64.ZERO;
		U64 portSpeedThreshold = policy.getPortSpeedThreshold();
				
		/* ���ݶ˿���������ֵ�Ƚ����ж��Ƿ�Ϊ�쳣���� */
		for(Entry<NodePortTuple, SwitchPortStatistics> e : portStats.entrySet()){
			rxSpeed = e.getValue().getRxSpeed();
			txSpeed = e.getValue().getTxSpeed();
			
			portSpeed = rxSpeed.add(txSpeed);
			logger.info("" + e.getKey().getNodeId() +" / " + e.getKey().getPortId() + " portSpeed: " + portSpeed.getBigInteger().toString());
			if(portSpeed.compareTo(portSpeedThreshold) > 0){
				abnormalTraffic.put(e.getKey(), e.getValue());
			}
		}
	}
}
