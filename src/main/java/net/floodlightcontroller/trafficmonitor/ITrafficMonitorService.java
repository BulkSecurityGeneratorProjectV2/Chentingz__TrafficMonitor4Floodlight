package net.floodlightcontroller.trafficmonitor;

import java.util.HashMap;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;

public interface ITrafficMonitorService extends IFloodlightService {

	// ��ȡ���н������˿�ͳ����Ϣ
	public HashMap<NodePortTuple, SwitchPortStatistics> getPortStatistics();
	
	// ��ȡָ���������˿�ͳ����Ϣ
	public SwitchPortStatistics getPortStatistics(DatapathId dpid, OFPort port);
	
	// ��ȡ����
	public Policy getPolicy();
	
	// ���ò���
	public void setPolicy(U64 portSpeedThreshold, String action, long actionDuration, U64 rateLimit);

	// ��ȡ���ò���
	public U64 getPortSpeedThreshold();
	public String getAction();
	public long getActionDuration();
	public U64 getRateLimit();
	
	// �������ò���
	public U64 setPortSpeedThreshold(U64 portSpeedThreshold);
	public String setAction(String action);
	public long setActionDuration(long actionDuration);
	public U64 setRateLimit(U64 rateLimit);

}
