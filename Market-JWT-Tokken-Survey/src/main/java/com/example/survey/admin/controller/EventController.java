package com.example.survey.admin.controller;

import com.example.survey.admin.model.AdminVO;
import com.example.survey.admin.model.EventVO;
import com.example.survey.admin.service.EventService;
import com.example.survey.model.BoardVO;
import com.example.survey.model.ReplyVO;
import com.example.survey.model.UserVO;
import com.example.survey.service.BoardService;
import com.example.survey.service.ReplyService;
import com.example.survey.service.UserService;
import com.example.survey.utils.SessionUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class EventController {
    final String key = "JWT";

    @Autowired
    EventService eventService;

    @Autowired
    BoardService boardService;

    @Autowired
    UserService userService;

    @Autowired
    ReplyService replyService;

    @InitBinder
    //DB에 날짜를 입력하기위해 쓰인다.
    protected void initBinder(WebDataBinder binder) {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        binder.registerCustomEditor(Date.class, new CustomDateEditor(dateFormat, true));
    }

    //이벤트 리스트
    @GetMapping("/eventlist")
    public String eventList(Model model, HttpServletRequest request) {

        EventVO eventVO = new EventVO();

        List<EventVO> eventlist = eventService.eventList(eventVO);
        model.addAttribute("eventlist", eventlist);

        AdminVO adminVO = SessionUtils.getAdmin(request);
        model.addAttribute("admin", adminVO);

        if (adminVO != null) {
            model.addAttribute("admin", adminVO);
            return "pages/admin/eventList";
        }
        return "redirect:/adminloginform";
    }

    //이벤트 작성 페이지
    @GetMapping("/eventlist/eventwrite")
    public String eventWrite(Model model, HttpServletRequest request) {
        AdminVO adminVO = SessionUtils.getAdmin(request);
        if (adminVO != null) {
            return "pages/admin/writeEvent";
        }
        return "redirect:/adminloginform";
    }

    //이벤트 작성 폼
    @PostMapping("/eventlist/eventInsert")
    public String eventInsert(EventVO eventVO, HttpServletRequest request) {
        AdminVO adminVO = SessionUtils.getAdmin(request);
        if (adminVO != null) {
            eventService.eventInsert(eventVO);
            return "redirect:/eventlist";
        } else {
            return "redirect:/adminloginform";
        }
    }

    //이벤트 상세보기
    @GetMapping("/eventlist/eventdetail/{seq}")
    private String eventDetail(@PathVariable int seq, Model model, HttpServletRequest request) {
        EventVO eventVO = eventService.eventDetail(seq);
        model.addAttribute("eventDetail", eventVO);

        AdminVO admin = SessionUtils.getAdmin(request);
        if (admin != null) {
            model.addAttribute("admin", admin);
            return "pages/admin/eventDetail";
        }
        return "redirect:/adminloginform";
    }

    //이벤트 삭제
    @GetMapping("/eventlist/eventDelete/{seq}")
    private String eventDelete(@PathVariable int seq, EventVO eventVO) {
        eventService.eventDelete(eventVO);
        return "redirect:/eventlist";
    }

    //해당 이벤트의 리스트 보기
    @GetMapping("/eventlist/eventsession/{seq}")
    private String eventSession(@PathVariable int seq, Model model, HttpServletRequest request) {

        EventVO eventVO = eventService.eventSession(seq);
        SessionUtils.setEventList(eventVO, request);

        List<BoardVO> boardlist = eventService.eventBoard(seq);
        model.addAttribute("boardlist", boardlist);

        AdminVO admin = SessionUtils.getAdmin(request);
        EventVO eventVO1 = SessionUtils.getEventList(request);
        model.addAttribute("admin", admin);
        model.addAttribute("events", eventVO1);
        return "pages/event/eventSessionList";
    }

    //해당 이벤트의 리스트 입력창 - 설문조사 창
    @GetMapping("/surveylist/{seq}/{id}")
    private String eventSessionwrite(@PathVariable int seq, @PathVariable String id, Model model, HttpServletRequest request, HttpServletResponse response, HttpSession session) throws UnsupportedEncodingException {
        try {
            //세션 만료시 만료 페이지로 보낸다.
            Map<String, Object> claimMap = userService.verifyJWT(id);
            if (claimMap == null) {
                return "pages/SessionExpiration";
            }

            EventVO eventVO = eventService.eventSession(seq);
            SessionUtils.setEventList(eventVO, request);

            model.addAttribute("eventSession", eventVO);

            String claimName = (String) claimMap.get("id");

            int eventSeq = Integer.parseInt(String.valueOf(claimMap.get("eventSeq")));
            //int eventSeq = (Integer) claimMap.get("event_seq");
            UserVO userVO = userService.urlTest(claimName);

            //설문조사 확인 여부
            UserVO isJoin = userService.isFinishSurvey(claimName);

            /**
             * 본 사용자가 설문조사에 답변을 완료했는지 여부를 판단한다.
             */
            /*boolean isJoin = userService.isFinishSurvey(eventSeq);
            if (isJoin) {
                // 이미 참여한 경우
                response.sendRedirect("/SurveyCompleted");
            } else {
                // 참여전인 경우, 설문조사 화면을 보여준다.
                return "pages/writeSurvey";
            }*/

            if (isJoin != null) {
                // 이미 참여한 경우
                //session.invalidate();
                return "redirect:/SurveyCompleted";
            } else if (SessionUtils.setUser(userVO, request)) {
                model.addAttribute("test", userVO);
                return "pages/event/writeEventSession";
            } else {
                return "pages/writeError";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/SurveyCompleted";
        }
    }

    //참여완료
    @GetMapping("/SurveyCompleted")
    private String SurveyCompleted() {
        return "pages/SurveyCompleted";
    }

    //이벤트 세션 리스트 폼
    @PostMapping("/eventlist/eventsession")
    private String eventSessionwrite(BoardVO boardVO, UserVO userVO, Model model, HttpServletRequest request, HttpSession session) {
        UserVO user = SessionUtils.getUser(request);
        if (user != null) {
            boardVO.setUserSeq(user.getSeq());
            eventService.eventBoardInsert(boardVO);
            userVO.setSeq(user.getSeq());
            //userService.userEventSeq(userVO);
            session.invalidate();
            return "redirect:/surveylist/1";
            //return "redirect:https://www.litstar.shop/";
        }
        return "pages/SessionExpiration";
    }

    //해당 이벤트 리스트 상세보기
    @GetMapping("/eventlist/eventsession/detail/{seq}")
    private String eventSessionDetail(@PathVariable int seq, Model model, HttpServletRequest request) throws Exception {
        BoardVO boardVO = boardService.boardDetail(seq);
        model.addAttribute("boardDetail", boardVO);
        model.addAttribute("boardHit", boardService.boardHit(seq));

        AdminVO admin = SessionUtils.getAdmin(request);
        model.addAttribute("admin", admin);

        List<ReplyVO> replyList = replyService.replyList(seq);
        model.addAttribute("replyList", replyList);
        if (admin != null) {
            return "pages/event/eventBoardDetail";
        }
        return "redirect:/adminloginform";
    }
}