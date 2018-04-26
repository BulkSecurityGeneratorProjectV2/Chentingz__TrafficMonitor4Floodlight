package net.floodlightcontroller.trafficmonitor;

import java.lang.Thread.State;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPortStatsEntry;
import org.projectfloodlight.openflow.protocol.OFPortStatsReply;
import org.projectfloodlight.openflow.protocol.OFPortStatsRequest;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import org.projectfloodlight.openflow.protocol.OFStatsRequest;
import org.projectfloodlight.openflow.protocol.OFStatsRequestFlags;
import org.projectfloodlight.openflow.protocol.OFStatsType;
import org.projectfloodlight.openflow.protocol.OFTable;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.dataformat.yaml.snakeyaml.nodes.NodeTuple;
import com.google.common.util.concurrent.ListenableFuture;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.threadpool.IThreadPoolService;



public class TrafficMonitor implements IOFMessageListener, IFloodlightModule {
	
	protected static final Logger logger = LoggerFactory.getLogger(TrafficMonitor.class);
	
	private IFloodlightProviderService 	floodlightProvider;
	private IOFSwitchService 			switchService;
	private IThreadPoolService 			threadPoolService;		// �̳߳�
	
	private static final long portStatsInterval = 10;			// �ռ��������˿�ͳ����������,��λΪ��
	private static ScheduledFuture<?> portStatsCollector;		// ���ڽ����̳߳صķ���ֵ���ռ�port_stats
	private static ScheduledFuture<?> flowStatsCollector;		// ���ڽ����̳߳صķ���ֵ���ռ�flow_stats
	
	private static HashMap<NodePortTuple, SwitchPortStatistics> prePortStatsBuffer;	// ���ڻ�����ǰ�Ľ������˿�ͳ����Ϣ����init�����н��г�ʼ��	
	private static HashMap<NodePortTuple, SwitchPortStatistics> portStatsBuffer;	// ���ڻ��浱ǰ�Ľ������˿�ͳ����Ϣ

	private boolean isFirstTime2CollectSwitchStatistics = true;
	/**
	 * �̣߳����������ռ��������˿�ͳ����Ϣ������˿ڽ������ʣ���������
	 * ��startPortStatsCollection()��ʹ��
	 */
	protected class PortStatsCollector implements Runnable{
		@Override
		public void run() {

			Map<DatapathId, List<OFStatsReply>> replies = getSwitchStatistics(switchService.getAllSwitchDpids(), OFStatsType.PORT);
			logger.info("Got port_stats_replies");
			
			/* ��һ���ռ�ͳ����Ϣ */
			if(isFirstTime2CollectSwitchStatistics){
				isFirstTime2CollectSwitchStatistics = false;
			
				/* ��¼�ռ���ͳ����Ϣ��prePortStatsReplies */
				savePortStatsReplies(prePortStatsBuffer, replies);
			}
			else{	/* ��ǰ�Ѿ��ռ�����һ��ͳ����Ϣ */
				savePortStatsReplies(portStatsBuffer, replies);
				
				if(prePortStatsBuffer!=null)
				for(Entry<NodePortTuple, SwitchPortStatistics> entry : prePortStatsBuffer.entrySet()){
					NodePortTuple npt = entry.getKey();
					
					/* ����˿ڽ������ʺͷ������ʲ����¶˿�ͳ����Ϣ */
					if( portStatsBuffer.containsKey(npt)){
						U64 rxBytes = portStatsBuffer.get(npt).getRxBytes().subtract(prePortStatsBuffer.get(npt).getRxBytes());
						U64 txBytes = portStatsBuffer.get(npt).getTxBytes().subtract(prePortStatsBuffer.get(npt).getTxBytes());
						
						long period = portStatsBuffer.get(npt).getDuration() - prePortStatsBuffer.get(npt).getDuration();
						
						U64 rxSpeed = U64.ofRaw(rxBytes.getValue() / period);
						U64 txSpeed = U64.ofRaw(txBytes.getValue() / period);
										
						/* ���� */
						portStatsBuffer.get(npt).setRxSpeed(rxSpeed);
						portStatsBuffer.get(npt).setTxSpeed(txSpeed);
					}					
				}
				
				/* ��ӡ�˿�ͳ����Ϣ */
				logger.info("ready to print stats");
				for(Entry<NodePortTuple, SwitchPortStatistics> e : portStatsBuffer.entrySet()){
					e.getValue().printPortStatistics();
				}

				
				/* ����prePortStatsBuffer */
				prePortStatsBuffer.clear();
				prePortStatsBuffer.putAll(portStatsBuffer);
				portStatsBuffer.clear();
			}
		
		}
		
		/**
		 *  ���յ���port_stats_reply���浽port_stats_buffer��
		 * @param portStatsBuffer �洢ͳ����Ϣ�Ļ�����
		 * @param replies stats_reply��Ϣ�б�
		 */
		private void savePortStatsReplies
			(HashMap<NodePortTuple, SwitchPortStatistics> portStatsBuffer, Map<DatapathId, List<OFStatsReply>> replies){
			for( Entry<DatapathId, List<OFStatsReply>> entry : replies.entrySet()){
				/* ����port_stats_reply��Ϣ��������ֶ�ת�浽SwitchPortStatistics����, */
				OFPortStatsReply psr = (OFPortStatsReply)entry.getValue().get(0);
				for(OFPortStatsEntry e :  psr.getEntries()){
					NodePortTuple npt = new NodePortTuple(entry.getKey(), e.getPortNo());
					SwitchPortStatistics sps = new SwitchPortStatistics();
					
					sps.setDpid(npt.getNodeId());
					sps.setPortNo(npt.getPortId());
					sps.setRxBytes(e.getRxBytes());
					sps.setTxBytes(e.getTxBytes());
					sps.setDurationSec(e.getDurationSec());
					sps.setDurationNsec(e.getDurationNsec());
					
					sps.setRxPackets(e.getRxPackets());
					sps.setTxPackets(e.getTxPackets());
					sps.setRxDropped(e.getRxDropped());
					sps.setTxDropped(e.getTxDropped());
					
					/* ��������ֶ� */
					// ...
					
					/* ���潻����id�˿ںŵĶ�Ԫ��Ͷ˿�ͳ����Ϣ�������� */
					portStatsBuffer.put(npt, sps);
				}	
			}
		}
	
	}
	
	
	/**
	 * �̣߳��������������Ե��ռ�������ͳ����Ϣ������flow_speed(������)
	 *
	 * ��startFlowStatsCollection()��ʹ��
	 */
	protected class FlowStatsCollector implements Runnable{
		@Override
		public void run() {

			Map<DatapathId, List<OFStatsReply>> replies = getSwitchStatistics(switchService.getAllSwitchDpids(), OFStatsType.FLOW);
			// ����flow_speed
		}
	}
	
	/**
	 * 	�����̳߳ط��񣬴����߳�������ִ��PortStatsCollector���е�run()��portStatsInterval������ִ������
	 */
	private void startPortStatsCollection(){
		
		portStatsCollector = threadPoolService
		.getScheduledExecutor()
		.scheduleAtFixedRate(new PortStatsCollector(), portStatsInterval, portStatsInterval, TimeUnit.SECONDS);

		logger.warn("Port statistics collection thread(s) started");
	}
	
	/**
	 * 	�����̳߳ط��񣬴����߳�������ִ��FlowStatsCollector���е�run()��portStatsInterval������ִ������
	 */
	private void startFlowStatsCollection(){
		
		flowStatsCollector = threadPoolService
		.getScheduledExecutor()
		.scheduleAtFixedRate(new FlowStatsCollector(), portStatsInterval, portStatsInterval, TimeUnit.SECONDS);
		
		logger.warn("Flow statistics collection thread(s) started");

	}
	
	
	/**
	 * ��ȡ���н�������ͳ����Ϣ��ͨ������GetStatsThread�߳������stats_request�ķ��ͺ�stats_reply�Ľ��գ�
	 * ������I/O�������߳������
	 * @param allSwitchDpids
	 * @param statsType
	 * @return
	 */
	public Map<DatapathId, List<OFStatsReply>> getSwitchStatistics  
		(Set<DatapathId> dpids, OFStatsType statsType) {
		HashMap<DatapathId, List<OFStatsReply>> dpidRepliesMap = new HashMap<DatapathId, List<OFStatsReply>>();
		
		List<GetStatsThread> activeThreads = new ArrayList<GetStatsThread>(dpids.size());
		List<GetStatsThread> pendingRemovalThreads = new ArrayList<GetStatsThread>();
		GetStatsThread t;
		for (DatapathId d : dpids) {
			t = new GetStatsThread(d, statsType);
			activeThreads.add(t);
			t.start();
		}

		/* Join all the threads after the timeout. Set a hard timeout
		 * of 12 seconds for the threads to finish. If the thread has not
		 * finished the switch has not replied yet and therefore we won't
		 * add the switch's stats to the reply.
		 */
		for (int iSleepCycles = 0; iSleepCycles < portStatsInterval; iSleepCycles++) {
			/* ����activeThread������߳��Ƿ���ɣ���������¼dpid��replies������curThread���뵽pendingRemovalThreads�Դ���� */
			for (GetStatsThread curThread : activeThreads) {
				if (curThread.getState() == State.TERMINATED) {
					dpidRepliesMap.put(curThread.getSwitchId(), curThread.getStatsReplies());
					pendingRemovalThreads.add(curThread);
				}
			}

			/* remove the threads that have completed the queries to the switches */
			for (GetStatsThread curThread : pendingRemovalThreads) {
				activeThreads.remove(curThread);
			}
			
			/* clear the list so we don't try to double remove them */
			pendingRemovalThreads.clear();

			/* if we are done finish early */
			if (activeThreads.isEmpty()) {
				break;
			}

			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				logger.error("Interrupted while waiting for statistics", e);
			}
		}

		return dpidRepliesMap;
	}
	
	/**
	 * ��ȡһ̨��������ͳ����Ϣ
	 * ����stats_request������stats_reply
	 * @param dpid
	 * @param statsType
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public List<OFStatsReply> getSwitchStatistics  
		(DatapathId dpid, OFStatsType statsType) {
		OFFactory my13Factory = OFFactories.getFactory(OFVersion.OF_13);
		OFStatsRequest<?> request = null;
		
		/* ����statsType���stats_request��Ϣ�ķ�װ */
		switch(statsType){
		case PORT:
			request = my13Factory.buildPortStatsRequest()
								 .setPortNo(OFPort.ANY)
								 .build();
			break;
			
		case FLOW:
			Match match = my13Factory.buildMatch().build();
			request = my13Factory.buildFlowStatsRequest()
								 .setMatch(match)
								 .setOutPort(OFPort.ANY)
								 .setOutGroup(OFGroup.ANY)
								 .setTableId(TableId.ALL)
								 .build();	
			break;
			
		default:
			logger.error("OFStatsType unknown,unable to build stats request");
		}
		
		/* ��ָ������������stats_request��������stats_reply */
		IOFSwitch sw = switchService.getSwitch(dpid);
		ListenableFuture<?> future = null;
		
		if(sw != null && sw.isConnected() == true)
			future = sw.writeStatsRequest(request);
		List<OFStatsReply> repliesList = null;
		try {
			repliesList = (List<OFStatsReply>) future.get(portStatsInterval*1000 / 2, TimeUnit.MILLISECONDS);
		} catch (Exception e) {
			logger.error("Failure retrieving statistics from switch {}. {}", sw, e);
		}
		return repliesList;
	}
	
	/**
	 * ��ȡͳ����Ϣ���߳���
	 * ��stats_msg��I/O���߳������
	 * ��run�����е���getSwitchStatistics�������stats_request�ķ��ͺ�stats_reply�Ľ���
	 *
	 */
	private class GetStatsThread extends Thread{
		private DatapathId dpid;
		private OFStatsType statsType;
		private	List<OFStatsReply> replies;
		
		public GetStatsThread(DatapathId dpid, OFStatsType statsType){
			this.dpid = dpid;
			this.statsType = statsType;
			this.replies = null;
		}
		
		public void run(){
			replies = getSwitchStatistics(dpid, statsType);
		}
		
		public DatapathId getSwitchId(){
			return dpid;
		}
		public List<OFStatsReply> getStatsReplies(){
			return replies;
		}
	}
	
	/*
	 * IFloodlightModule��ʵ��
	 */
	
	/**
	 * ��OFMessage��������һ��ID
	 */
	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return TrafficMonitor.class.getSimpleName();
	}



	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	
	/**
	 * ��֪ģ���������floodlight����ʱ����ģ����ء�
	 */
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		// TODO Auto-generated method stub
		   Collection<Class<? extends IFloodlightService>> l =
			        new ArrayList<Class<? extends IFloodlightService>>();
			    l.add(IFloodlightProviderService.class);
			    l.add(IOFSwitchService.class);
			    l.add(IThreadPoolService.class);
			    return l;
	}

	/**
	 * ���ڿ������������ڱ����ã�����Ҫ�����Ǽ���������ϵ����ʼ�����ݽṹ��
	 */
	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		// TODO Auto-generated method stub
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		switchService = context.getServiceImpl(IOFSwitchService.class);
		threadPoolService = context.getServiceImpl(IThreadPoolService.class);
		prePortStatsBuffer = new HashMap<NodePortTuple, SwitchPortStatistics>();
		portStatsBuffer = new HashMap<NodePortTuple, SwitchPortStatistics>();
		
	}


	/**
	 * ģ������
	 */
	@Override
	public void startUp(FloodlightModuleContext context){
		/* �ռ��������˿�ͳ����Ϣ������port_speed */
		startPortStatsCollection();
	
		/* �ռ�������ͳ����Ϣ������flow_speed */
	//	startFlowStatsCollection();

	}
	
	/**
	 * �� OF��Ϣ�Ĵ�����Ҫ��PACKET_IN��Ϣ��
	 */
	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		// TODO Auto-generated method stub
		return null;
	}
	
	
	
	

	

	
	
/*************************************************** ����ɾ�� ******************************************************************/	
	
	
	/**
	 *  �򽻻�������port_stats_request��Ϣ,������future���װ��port_stats_reply��Ϣ
	 */
	private void sendPortStatsRequest()
	{
		OFFactory my13Factory = OFFactories.getFactory(OFVersion.OF_13);

		/*
		Set<OFStatsRequestFlags> set = new HashSet<OFStatsRequestFlags>();
		set.add(OFStatsRequestFlags.REQ_MORE);
		*/
		
		// ��װport_stats_request
		OFPortStatsRequest request = my13Factory.buildPortStatsRequest()
				.setPortNo(OFPort.ANY)
		//		.setFlags(set)
				.build();

		HashSet<IOFSwitch> switchSet = (HashSet<IOFSwitch>) getAllSwitchInstance();
		IOFSwitch mySwitch;
			
		// ��switchSet��ȡ������������ʵ����������port_stats_request����Щ������
		Iterator<IOFSwitch> it4switchSet = switchSet.iterator();
		while(it4switchSet.hasNext()){
			mySwitch = it4switchSet.next();
			
			if(mySwitch != null && mySwitch.isConnected())
			{
	
				// ����port_stats_request�����port_stats_reply��װ��future����
				ListenableFuture<List<OFPortStatsReply>> future = mySwitch.writeStatsRequest(request);			
				
				processPortStatsReply(future, mySwitch);
			}
			else
			{
				logger.error("Switch dpid: { " + mySwitch.getId() + " } is not connected");
			}
		}
	}
	
	/**
	 * 	����future�е�port_stats_reply��Ϣ����ͳ����Ϣ��ӡ
	 * @param future ��װ��port_stats_reply 
	 * @param sw ����port_stats_reply�Ľ�����
	 * 
	 */
	private void processPortStatsReply(ListenableFuture<List<OFPortStatsReply>> future, IOFSwitch sw){
		if(future == null){
			logger.error("future null at processPortStatsReply() ");
			return;
		}
		else{
			logger.info("start processing port_stats_reply");
			
			List<OFPortStatsReply> replyList = null;
			try {
				replyList = future.get();
				logger.info("replyList_length:" + replyList.toArray().length);	// replyList����һ�㶼Ϊ1��ֻ�յ�һ��reply
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ExecutionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			// ��ӡ�˿�ͳ����Ϣ
			for(OFPortStatsReply psr : replyList ){
				for(OFPortStatsEntry entry : psr.getEntries()){
					
					String printInfo = "\n[ switch dpid : " + sw.getId() + " ]\n";
					printInfo += "[ port no : " + entry.getPortNo() + " ]\n";
					printInfo += "[ TxPackets : " + entry.getTxPackets()+ " ]\n";
					printInfo += "[ RxPackets : " + entry.getRxPackets()+ " ]\n";
	
					// Add here: ��Ӹ���Ҫ��ӡ�Ķ˿�ͳ����Ϣ
					
					logger.info(printInfo);
				}
				/* ���������Ķ˿�ͳ����Ϣ����
				swPortStatsReplyBuffer.put(sw.getId(), psr); */

			}	
			
			
		}

	}
	
	
	
	/**
	 *  ��ȡ���н�������ʵ��
	 * 	@return IOFSwitch�ļ���
	 */
	private Set<IOFSwitch> getAllSwitchInstance(){
		// ���ڴ洢IOFSwitch�ļ���
		Set<IOFSwitch> switchSet = new HashSet<IOFSwitch>();
		
		Set<DatapathId>	dpidSet = switchService.getAllSwitchDpids();
		DatapathId dpid;
		
		Iterator<DatapathId> it4dpidSet = dpidSet.iterator();
		while(it4dpidSet.hasNext()){
			dpid = it4dpidSet.next();
			switchSet.add(switchService.getSwitch(dpid));
		}
		
		return switchSet;
		
	}	
}

