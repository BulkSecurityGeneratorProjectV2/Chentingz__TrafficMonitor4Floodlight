package net.floodlightcontroller.trafficmonitor.web;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.statistics.web.BandwidthResource;
import net.floodlightcontroller.statistics.web.SwitchStatisticsWebRoutable;
import net.floodlightcontroller.trafficmonitor.ITrafficMonitorService;
import net.floodlightcontroller.trafficmonitor.SwitchPortStatistics;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ��������ڴ���REST API���󣬾��������ǻ�ȡurl���ֶΣ�������Щ�ֶ���Ϊ��������ITrafficMonitorService�ķ���
 * ��/wm/trafficmonitor/portstats/{dpid}/{port}/json
 * ȡ��dpid��port��������ITrafficMonitorService��getPortStatistics()����ȡָ���������˿ڵ�ͳ����Ϣ
 */
public class PortStatsResource extends ServerResource {
	private static final Logger logger = LoggerFactory.getLogger(BandwidthResource.class);
	
	@Get("json")
	public Object retrieve() {
		
		ITrafficMonitorService trafficMonitorService = (ITrafficMonitorService)getContext().getAttributes().get(ITrafficMonitorService.class.getCanonicalName());
		
		/* ��url·����ȡ���ַ���dpid��port */
		String dpid_str = (String) getRequestAttributes().get(TrafficMonitorWebRoutable.DPID_STR);
	    String port_str = (String) getRequestAttributes().get(TrafficMonitorWebRoutable.PORT_STR);
		
	    
	    /* �����ַ���ת�� */
		DatapathId dpid = DatapathId.of(dpid_str);
		OFPort 	   port = OFPort.of(Integer.parseInt(port_str));
		
	    
		/* ����dpid��portͨ��ITrafficMonitorService��ȡ�˿�ͳ����Ϣ */
	    Set<SwitchPortStatistics> spsSet = new HashSet<SwitchPortStatistics>();
		if(dpid != null && port !=null){
			SwitchPortStatistics sps = trafficMonitorService.getPortStatistics(dpid, port);
			if(sps != null){
				spsSet.add(sps);
			}
			else
			{
				logger.info("port stats null");
			}
		}
		
		return spsSet;
	}
}
