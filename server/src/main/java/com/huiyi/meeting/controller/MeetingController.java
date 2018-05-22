package com.huiyi.meeting.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.dto.huiyi.meeting.entity.CHQSResult;
import com.dto.huiyi.meeting.entity.ProcessInstanceDto;
import com.dto.huiyi.meeting.entity.meetingDto.ProcessStartParameter;
import com.dto.huiyi.meeting.util.Constants;
import com.dto.huiyi.meeting.util.TimeDateFormat;
import com.huiyi.meeting.dao.mapper.MeetingMeetingMapper;
import com.huiyi.meeting.dao.model.MeetingMeeting;
import com.huiyi.meeting.dao.model.MeetingMeetingExample;
import com.huiyi.meeting.rpc.api.MeetingMeetingService;
import com.huiyi.service.HttpClientService;
import com.zheng.common.base.BaseResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

import static com.dto.huiyi.meeting.util.Constants.ERROR_CODE;
import static com.dto.huiyi.meeting.util.Constants.SUCCESS_CODE;

@Controller
@RequestMapping("/meeting")
@Api(value = "会议管理", description = "对会议进行创建，查询，挂起，取消操作")
public class MeetingController {

    @Autowired
    MeetingMeetingMapper meetingMeetingMapper;

    @Autowired
    HttpClientService httpClientService;

    @Autowired
    MeetingMeetingService meetingMeetingService;

    @ApiOperation(value = "查询所有正在进行中的会议")
    @RequestMapping(value = "listActives", method = RequestMethod.GET)
    @ResponseBody
    public BaseResult listMeeting(){
        List<MeetingMeeting> activeMeetings = new ArrayList<>();
        try {
            CHQSResult<List<ProcessInstanceDto>> processInstanceDtos =  httpClientService.getCHQSData(Constants.CHQSURL + "process/listActiveProcess", null, new TypeReference<CHQSResult<List<ProcessInstanceDto>>>(){});
            if(processInstanceDtos == null)
                return new BaseResult(SUCCESS_CODE, "empty", null);

            for(ProcessInstanceDto d: processInstanceDtos.getData()){
                String bussinessKey = d.getBusinessKey();
                int id = Integer.parseInt(bussinessKey.split("\\.")[1]);
                activeMeetings.add(meetingMeetingService.selectByPrimaryKey(id));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return new BaseResult(SUCCESS_CODE, "success", activeMeetings);
    }

    @ApiOperation(value = "开始一场会议")
    @RequestMapping(value = "startMeeting", method = RequestMethod.POST)
    @ResponseBody
    public BaseResult startMeeting(@RequestBody MeetingMeeting meeting){

        int id = -1;
        //save one meeting

        if(meeting != null){
            if(meeting.getBeginat() == null) meeting.setBeginat(new Date()); // 开始时间为空的话，设置为立即开始
            int affectCount = meetingMeetingService.insert(meeting);
            if(affectCount == 1){
                MeetingMeetingExample example = new MeetingMeetingExample();
                example.createCriteria().andBeginatEqualTo(meeting.getBeginat());
                List<MeetingMeeting> ms = meetingMeetingMapper.selectByExample(example);
                meeting = ms.get(0);
            }
            id = meeting.getId();
        }

        // start the meeting process
        String process_id = MeetingMeeting.class.getSimpleName();
        String bussiness_key = process_id + "." + id;
        String chqsUrl = Constants.CHQSURL + "process/start";
        CHQSResult result = null;
        // prepare the parameters
        // date format: 2011-03-11T12:13:14
        Date beginAt = meeting.getBeginat();
        String activitiTime = TimeDateFormat.formatTime(beginAt);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("beginAt", activitiTime);  //

        ProcessStartParameter processStartParameter = new ProcessStartParameter();
        processStartParameter.setBussinessId(bussiness_key);
        processStartParameter.setParameters(parameters);
        processStartParameter.setProcessId(process_id);
        try {
            result = httpClientService.postCHQSJson(chqsUrl, JSON.toJSONString(processStartParameter), new TypeReference<CHQSResult>(){});
        } catch (IOException e) {
            e.printStackTrace();
            return new BaseResult(ERROR_CODE, "system error", null);
        }

        if(result.code == Constants.SUCCESS_CODE){
            MeetingMeeting createdMeeting = meetingMeetingService.selectByPrimaryKey(id);
            return new BaseResult(SUCCESS_CODE, "success", createdMeeting);
        }

        return new BaseResult(ERROR_CODE, "system error", null);

    }

    @ApiOperation(value = "取消一场会议")
    @RequestMapping(value = "cancelMeeting/{id}", method = RequestMethod.GET)
    @ResponseBody
    public BaseResult cancelMeeting(@PathVariable int id){
        String bussinessKey = MeetingMeeting.class.getSimpleName() + "." + id;
        String chqsUrl = Constants.CHQSURL + "process/stopByBussinesskey/" + bussinessKey;

        try {
            CHQSResult result = httpClientService.getCHQSData(chqsUrl, null, new TypeReference<CHQSResult>(){});
            if(result!=null && result.getCode() == SUCCESS_CODE){

                return new BaseResult(SUCCESS_CODE, "success", null);
            }else{
                return new BaseResult(ERROR_CODE, "system error", null);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        return new BaseResult(ERROR_CODE, "system error", null);
    }

    @ApiOperation(value = "查看一场会议")
    @RequestMapping(value = "checkMeeting/{id}", method = RequestMethod.GET)
    @ResponseBody
    public BaseResult viewMeeting(@PathVariable int id){
        MeetingMeeting result = meetingMeetingService.selectByPrimaryKey(id);
        return new BaseResult(SUCCESS_CODE, "success", result);
    }
}