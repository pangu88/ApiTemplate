package com.huiyi.meeting.service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import com.huiyi.dao.*;
import com.huiyi.dao.externalMapper.ExternalMeetingParticipantMapper;
import com.huiyi.meeting.dao.model.*;
import com.huiyi.meeting.rpc.api.*;
import org.activiti.engine.history.HistoricProcessInstance;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.dto.huiyi.meeting.entity.register.ComparisonResultDto;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeetingRegisterService {

	private static Logger LOGGER = LoggerFactory.getLogger(MeetingRegisterService.class);
	
	@Autowired
	private MeetingMeetingService meetingMeetingService;
	@Autowired
	private MeetingParticipantService meetingParticipantService;
	@Autowired
	private MeetingRegistService meetingRegistService;
	@Autowired
	private ExternalMeetingParticipantMapper externalMeetingParticipantMapper;
	@Autowired
	private MeetingStatementRegistService meetingStatementRegistService;

	@Autowired
    private MeetingStatementService meetingStatementService;
	
	
	public String getObjectDescription(HistoricProcessInstance pi) {
		// TODO Auto-generated method stub
		String[] businessKeyArray = pi.getBusinessKey().split("_");
		if(businessKeyArray.length != 2) {
			return "无法解析businessKey或未指定"+pi.getBusinessKey();
		}
		String objectType = businessKeyArray[0];
		if(!StringUtils.isNumeric(businessKeyArray[1])) {
			return "业务对象ID异常"+businessKeyArray[1];
		}
		int objectId = Integer.parseInt(businessKeyArray[1]);
		LOGGER.debug("对象类型："+objectType +",对象ID："+objectId);
		if(objectType.equals(MeetingMeeting.class.getSimpleName())) {
			MeetingMeeting obj = meetingMeetingService.selectByPrimaryKey(objectId);
			return obj.toString();
		}
		else if(objectType.equals(MeetingParticipant.class.getSimpleName())) {
			MeetingParticipant obj = meetingParticipantService.selectByPrimaryKey(objectId);
			return obj.toString();
		}
		else if(objectType.equals(MeetingRegist.class.getSimpleName())) {
			MeetingRegist obj = meetingRegistService.selectByPrimaryKey(objectId);
			return obj.toString();
		}
		return "不支持的businessKey"+pi.getBusinessKey();
	}

	public List<ComparisonResultDto> reconsile(List<MeetingStatement> statements, List<ExternalMeetingParticipant> externalMeetingParticipants, List<String> excludingCompanies) {
		List<ComparisonResultDto> comparisonResultDtos = new ArrayList<>();
		if(statements == null || statements.size() ==0){
			return comparisonResultDtos;
		}

		Set<String> companyies = new HashSet<>();
		for(MeetingStatement statement:statements){
			companyies.add(statement.getCompanyname());
		} // 统计所有的公司

		for(String company: companyies){
			ComparisonResultDto comparisonResultDto = new ComparisonResultDto();
			comparisonResultDto.setCompanyName(company);
			float participantShoulPay = this.sumParticipantRegistFee(externalMeetingParticipants, company);
			comparisonResultDto.setParticipantFeeTotal(participantShoulPay);
			float bankStaementTotal = this.sumStatementbyCompany(statements, company);
			comparisonResultDto.setStatementTotal(bankStaementTotal);
			if(participantShoulPay == bankStaementTotal)
				comparisonResultDto.setMatch(true);
			else
				comparisonResultDto.setMatch(false);
			List<MeetingStatement> company_statements = new ArrayList<>();
			for(MeetingStatement statement:statements){
				if(statement.getCompanyname().equalsIgnoreCase(company))
					company_statements.add(statement);
			}
			comparisonResultDto.setStatements(company_statements);

			List<ExternalMeetingParticipant> company_participants = new ArrayList<>();
			for(ExternalMeetingParticipant participant:externalMeetingParticipants){
				if(participant.getCompanyName().equalsIgnoreCase(company))
					company_participants.add(participant);
			}
//			comparisonResultDto.setParticipants(company_participants);
			comparisonResultDtos.add(comparisonResultDto);
		}

		List<ComparisonResultDto> resultDtos = new ArrayList<>();
		//如果有公司需要排除， 就放这里
		if(excludingCompanies != null){
			for(ComparisonResultDto dto:comparisonResultDtos){
				boolean contains = excludingCompanies.contains(dto.getCompanyName()); //说明该公司应该被排除
				if(contains){
					resultDtos.add(dto);
				}
			}
			return resultDtos;
		}
		return comparisonResultDtos;
	}

	private float sumStatementbyCompany(List<MeetingStatement> statements, String company){
		float result = 0;
		for(MeetingStatement statement:statements){
			if(company.equalsIgnoreCase(statement.getCompanyname()))
				result += statement.getFee();
		}

		return result;
	}

	private float sumParticipantRegistFee(List<ExternalMeetingParticipant> participants, String company){

		float result = 0;
		for(ExternalMeetingParticipant participant:participants){
			if(company.equalsIgnoreCase(participant.getCompanyName()))
				result += participant.getFee();
		}
		return result;
	}


	// 将已经确认的人员付费信息插入外部表:CZH
	public void insertIntoCZH(MeetingParticipant meetingParticipant){
		// 根据手机号检查该人是否已经在数据库里面了，如果在就不添加了
		String mobile = meetingParticipant.getTelephone();
		CZH czh = externalMeetingParticipantMapper.getByPhone(mobile);
		if(null == czh){
            CZH czhNew = new CZH();


        }else {
		    //已经存在，直接返回
		    return;
        }
	}

	public List<ComparisonResultDto> reconsile(String filepath) {
		// TODO Auto-generated method stub';
		double per = 1000.0;
		Map<String,List<MeetingParticipant>> indexes = new HashMap<>();
		Map<String,Integer> companyMap = new HashMap<>();
		try {
			InputStream is = new FileInputStream(filepath);
			XSSFWorkbook workbook = new XSSFWorkbook(is);
			XSSFSheet worksheet = workbook.getSheetAt(0);
			int rownum = worksheet.getLastRowNum();
			for(int i=1;i<rownum;i++) {
				XSSFRow row = worksheet.getRow(i);
				if(row.getCell(1)==null)
					continue;
				String companyName = row.getCell(0).toString();
				indexes.put(companyName, new ArrayList<MeetingParticipant>());
				companyMap.put(companyName, (int)(row.getCell(1).getNumericCellValue()/per));
			}
			workbook.close();
		}catch(IOException ioe) {
			LOGGER.error(ioe.getMessage());
		}
		List<MeetingParticipant> mpList = meetingParticipantService.selectByExample(new MeetingParticipantExample());
		for(MeetingParticipant mp : mpList) {
			List<MeetingParticipant> vList = indexes.get(mp.getCompany());
			if(vList == null) {
				vList = new ArrayList<>();
				indexes.put(mp.getCompany(), vList);
			}
			vList.add(mp);
			Integer left = companyMap.get(mp.getCompany());
			companyMap.put(mp.getCompany(),left==null?-1:left-1);
		}
		List<ComparisonResultDto> list = new ArrayList<>();
		for(String cn : indexes.keySet()) {
			ComparisonResultDto crd = new ComparisonResultDto();
			crd.setCompanyName(cn);
			crd.setMatch(companyMap.get(cn)==0);
			list.add(crd);
			LOGGER.debug(cn+" match? "+crd.isMatch());
		}
		return list;
	}


	public List<ExternalMeetingParticipant> convertToExternalMeetingParticipants(List<MeetingParticipant> participants){

		List<ExternalMeetingParticipant> externalMeetingParticipants = new ArrayList<>();
		for(MeetingParticipant participant : participants){
			ExternalMeetingParticipant externalMeetingParticipant = new ExternalMeetingParticipant();
			externalMeetingParticipant.setId(participant.getId());
			externalMeetingParticipant.setCompanyName(participant.getCompany());
			externalMeetingParticipant.setParticipantName(participant.getName());
			externalMeetingParticipant.setFee(participant.getMeetingfee());
			externalMeetingParticipant.setRegistTime(participant.getMeetingregistertime());
			externalMeetingParticipants.add(externalMeetingParticipant);
		}
		return externalMeetingParticipants;
	}


	public List<String> getSalemanForThisCompany(String company){
		List<ExternalSales> externalSales = externalMeetingParticipantMapper.getSalesByCompany(company);
		List<String> sales = new ArrayList<>();
		for(ExternalSales sale:externalSales){
			sales.add(sale.getSALES());
		}

		return sales;
	}

	public List<String> getCompanyBySaleman(String saleMan){
		List<ExternalSales> externalSales = externalMeetingParticipantMapper.getCompanyBySales(saleMan);
		List<String> companies = new ArrayList<>();
		for(ExternalSales sale:externalSales){
			companies.add(sale.getCOMPANY());
		}
		return companies;
	}


	public List<MeetingStatement> getStatementsForComparison(int meetingId){
        MeetingStatementExample meetingStatementExample = new MeetingStatementExample();
        meetingStatementExample.createCriteria()
                .andIsdisableEqualTo(false)
                .andMeetingidEqualTo(meetingId);
        return meetingStatementService.selectByExample(meetingStatementExample);
    }


    public List<JCI_ORDER> getUnpaidOrders(){
	    //如何判断是未付的呢？
//        List<JCI_ORDER> jci_orders = externalMeetingParticipantMapper.getAllUnpaidOrders();
//        List<JCI_ORDER> jci_unpaid_orders = new ArrayList<>();
//        for(JCI_ORDER unpaidOrder:jci_orders){
//            CZH paidOrder = externalMeetingParticipantMapper.getCZHOrderByOrderno(unpaidOrder.getNO());
//            if(null == paidOrder){
//                jci_unpaid_orders.add(unpaidOrder);
//            }
//            //如果会费未付，那么酒店的肯定未付
//        }
//        return jci_unpaid_orders;

        return externalMeetingParticipantMapper.getAllUnpaidOrders();
    }

    public List<ComparisonResultDto> reconcileStatementAndUnpaidOrders(List<MeetingStatement> statementList, List<JCI_ORDER> jci_orders){
        //银行流水过来的单据  公司|金额
        //jci_orders为所有未确认的订单  金额可能是会务费  也可能是酒店费
        if(statementList == null || statementList.size() ==0){
            return new ArrayList<>();  //返回空
        }

        // 将银行流水按照公司合并
        Map<String, List<MeetingStatement>> groupedStatements = this.aggreateStatementByCompany(statementList);

        List<ComparisonResultDto>  comparisonResultDtos = new ArrayList<>();
        for(String company:groupedStatements.keySet()){
                ComparisonResultDto resultDto = new ComparisonResultDto();
                resultDto.setCompanyName(company);
                List<MeetingStatement> statements = groupedStatements.get(company);
                resultDto.setStatements(statements);

                for(MeetingStatement statement:statements){
                    List<JCI_ORDER> gsmcOrder = new ArrayList<>();
                    float totalShouldPay = 0;
                    for(JCI_ORDER order:jci_orders){ //计算总价
                        if(order.getGSMC().equalsIgnoreCase(company)){
                            gsmcOrder.add(order);
                            totalShouldPay += order.getTOTAL();
                        }
                    }

                    List<JCI_ORDER_HOTEL> hotel_orders = new ArrayList<>();

                    for(JCI_ORDER order:gsmcOrder){
                        JCI_ORDER_HOTEL order_hotel = externalMeetingParticipantMapper.getHotelOrderByOrderno(order.getNO()); //试图获取酒店订单
                        if(null == order_hotel)
                            continue;
                        else{
                            hotel_orders.add(order_hotel);
                        }
                    }

                    resultDto.setStatementTotal(statement.getFee());
                    resultDto.setJci_orders(gsmcOrder);
                    resultDto.setJci_order_hotels(hotel_orders);
                    resultDto.setParticipantFeeTotal(totalShouldPay);
                    if(totalShouldPay == statement.getFee()){
                        resultDto.setMatch(true);
                    }else{
                        resultDto.setMatch(false);
                    }
                    comparisonResultDtos.add(resultDto);
                }
        }


        return comparisonResultDtos;
    }

    private Map<String, List<MeetingStatement>> aggreateStatementByCompany(List<MeetingStatement> statements){
        if(null == statements)
            return new HashMap<>();
        Map<String, List<MeetingStatement>> companyNameGrouped = new HashMap<>();
        for(MeetingStatement statement:statements){
            String company = statement.getCompanyname();
            MeetingStatement containingStatement = null;

            if(companyNameGrouped.keySet().contains(company)){
                companyNameGrouped.get(company).add(statement);
            }else{
                List<MeetingStatement> statementList = new ArrayList<>();
                statementList.add(statement);
                companyNameGrouped.put(company, statementList);
            }


        }

        return companyNameGrouped;
    }


    //财务人员确认费用是否正确
//    @Transactional
    public boolean confirmMeetingFeeAndHotelFeeByAccount(List<ComparisonResultDto> comparisonResultDtos, MeetingRegist meetingRegist){
		for(ComparisonResultDto dto:comparisonResultDtos) {
			for (MeetingStatement statement : dto.getStatements()) {
                //将那些match的记录插入CZH表
                if(dto.isMatch()) {
                    List<JCI_ORDER> orders = dto.getJci_orders();
                    MeetingStatementRegist meetingStatementRegist = new MeetingStatementRegist();
                    meetingStatementRegist.setStatementid(statement.getId());
                    meetingStatementRegist.setMeetingregistid(meetingRegist.getId());
                    meetingStatementRegistService.insert(meetingStatementRegist);//设置关联关系
                    statement.setIsdisable(true); //防止以后对比账户再次出现
                    meetingStatementService.updateByPrimaryKey(statement);  //将银行流水设置为已经被确认
                    for (JCI_ORDER order : orders) {
                        CZH czh = new CZH();
                        BeanUtils.copyProperties(order, czh);
                        czh.setCM(order.getCN());
                        czh.setSFCJWY(order.getCJWY());
                        czh.setCASH((int) order.getTOTAL());
                        czh.setID(null);
                        externalMeetingParticipantMapper.confirmParticipantFee(czh);
                        //检查是否有酒店订单
                        String orderNo = order.getNO();
                        //更改酒店订单为已经付费
                        externalMeetingParticipantMapper.updateHotelOrder("1", orderNo);
                    }
                }
			}
		}
        return true;
    }


    public List<ComparisonResultDto> salemanViewPayment(String userId, List<ComparisonResultDto> items){
        List<ComparisonResultDto> result = new ArrayList<>();
        List<ExternalSales> externalSales = externalMeetingParticipantMapper.getCompanyBySales(userId);
        for(ComparisonResultDto dto:items){
            boolean isBelongToThisUser = false;
            for(ExternalSales sales:externalSales){
                if(sales.getCOMPANY().equalsIgnoreCase(dto.getCompanyName())){
                    isBelongToThisUser = true;
                }
            }
            if(isBelongToThisUser){
                result.add(dto);
            }
        }
        return result;
    }

    //根据orderNo 将会费或者酒店费记录删除
//    @Transactional
    public boolean refundFee(String orderNo){
        CZH czh = externalMeetingParticipantMapper.getCZHOrderByOrderno(orderNo);
        if(null == czh ){
            return false;
        }

        externalMeetingParticipantMapper.deleteMeetingFee(orderNo);

        JCI_ORDER_HOTEL hotel_order = externalMeetingParticipantMapper.getHotelOrderByOrderno(orderNo);
        if(null != hotel_order){
            externalMeetingParticipantMapper.deleteHotelFee(orderNo);
        }

        return true;
    }
}
