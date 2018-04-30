package net.floodlightcontroller.trafficmonitor;

import java.util.HashMap;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;

public interface ITrafficMonitorService extends IFloodlightService {

	// ��ȡ���н������˿�ͳ����Ϣ
	public HashMap<NodePortTuple, SwitchPortStatistics> getPortStatistics();
	
	// ��ȡָ���������˿�ͳ����Ϣ
	public SwitchPortStatistics getPortStatistics(DatapathId dpid, OFPort port);
	
}
