package com.cloud.controller;

import com.cloud.service.*;
import org.apache.catalina.User;
import org.apache.commons.validator.GenericValidator;
import org.hibernate.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;
import cucumber.api.java.cs.A;
import org.apache.commons.validator.GenericValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.cloud.domain.*;

import java.sql.Array;
import java.sql.Timestamp;
import java.util.*;

import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpServletRequest;


@Slf4j
@RestController
public class CustomerController {
    @Autowired // This means to get the bean called userRepository
    // Which is auto-generated by Spring, we will use it to handle the data
    private UserRepository userRepository;
    @Autowired
    private QuestionRepository questionRepository;
    @Autowired
    private AnswerRepository answerRepository;
    @Autowired
    private UserService userService;
    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private FileStorageService fileStorageService;
    @Autowired
    private FileInfoRepository fileInfoRepository;

    @PostMapping(path="/v1/user",produces = "application/json") // Map ONLY POST Requests
    @ResponseStatus(value = HttpStatus.CREATED)
    public ResponseEntity createUser (@RequestBody userInfo info ) {
        if(info.getPassword()==null||info.getEmail_address()==null||info.getFirst_name()==null||info.getLast_name()==null){
            //System.out.println("1");
            return new ResponseEntity<>(HttpStatus.valueOf(400));
        }
        if(info.getLast_name().equals("")||info.getFirst_name().equals("")){
            //System.out.println("2");
            return new ResponseEntity<>(HttpStatus.valueOf(400));
        }

        if(!userService.pwdValidation(info.getPassword())){
           // System.out.println("3");
            return new ResponseEntity<>(HttpStatus.valueOf(400));
        }
        if(!userService.emailVaildation(info.getEmail_address())){
           // System.out.println("4");
            return new ResponseEntity<>(HttpStatus.valueOf(400));
        }
        UserAccount n= new UserAccount();
        n.setFirst_name(info.getFirst_name());
        n.setLast_name(info.getLast_name());
        n.setPassword(info.getPassword());
        n.setEmailAddress(info.getEmail_address());
        n.setAccount_updated(new Timestamp(System.currentTimeMillis()).toString());
        n.setAccount_created(new Timestamp(System.currentTimeMillis()).toString());
        if(userService.CheckIfEmailExists(n.getEmailAddress())){
            return new ResponseEntity<>(HttpStatus.valueOf(400));
        }

        userService.saveWithEncoder(n);
        return new ResponseEntity<>(new userInfo_noPwd(n.getId(),n.getFirst_name(),n.getLast_name(),n.getEmailAddress(),n.getAccount_created(),n.getAccount_updated()),HttpStatus.CREATED);
    }


    //Get User Info
    @GetMapping(path="/v1/user/self",produces = "application/json")
    public ResponseEntity getUserInfo (){

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
            if(authentication.getName().equals("anonymousUser")) return new ResponseEntity(HttpStatus.valueOf(401));
            System.out.println(authentication.getName());
            UserAccount n=userService.findByEmail(authentication.getName());

            return new ResponseEntity(new userInfo_noPwd(n.getId(),n.getFirst_name(),n.getLast_name(),n.getEmailAddress(),n.getAccount_created(),n.getAccount_updated()),HttpStatus.OK);



    }

    //Update user info
    @PutMapping(path="/v1/user/self",produces = "application/json")
    public ResponseEntity updateUserInfo (@RequestBody UserAccount_v2 n){
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        //System.out.println(authentication.getName());
        if(authentication.getName().equals("anonymousUser")) return new ResponseEntity(HttpStatus.valueOf(401));
        UserAccount old=userService.findByEmail(authentication.getName());

        if(n.getId()!=null||n.getAccount_created()!=null||n.getAccount_updated()!=null||!old.getEmailAddress().equals(n.getEmail_address())){
            return new ResponseEntity(HttpStatus.valueOf(400));
        }

        int i=0;
        if(n.getFirst_name()!=null && !"".equals(n.getFirst_name())){
            old.setFirst_name(n.getFirst_name());
            i++;
        }
        if(n.getLast_name()!=null && !"".equals(n.getLast_name())){
            old.setLast_name(n.getLast_name());
            i++;
        }
        if(n.getPassword()!=null && !"".equals(n.getPassword())){
            if(!userService.pwdValidation(n.getPassword())){
                return new ResponseEntity(HttpStatus.valueOf(400));
            }
            //System.out.println("password changed");
            old.setPassword(bCryptPasswordEncoder.encode(n.getPassword()));
            i++;
        }
        old.setAccount_updated(new Timestamp(System.currentTimeMillis()).toString());
        userService.update(old);
        return new ResponseEntity(HttpStatus.valueOf(204));
    }

    @PostMapping(path="/v1/question",produces = "application/json") // Map ONLY POST Requests
    public ResponseEntity createBill (@RequestBody QuestionInfo info ) {
        try {
            Authentication authentication =
                    SecurityContextHolder.getContext().getAuthentication();
            if (authentication.getName().equals("anonymousUser")) return new ResponseEntity(HttpStatus.valueOf(401));
//            if (QuestionInfoCheck(info)) return new ResponseEntity(HttpStatus.valueOf(400));

            UserAccount user = userService.findByEmail(authentication.getName());

            Question b = new Question();
            CategoryInfo c = new CategoryInfo();


            for (CategoryInfo categoryInfo : info.getCategories()) {
                categoryRepository.save(categoryInfo);
            }

            b.setQuestion_text(info.getQuestion_text());
            b.setUserId(user.getId());
            b.setCreated_timestamp(new Timestamp(System.currentTimeMillis()).toString());
            b.setUpdated_timestamp(new Timestamp(System.currentTimeMillis()).toString());
            b.setAnswers(null);
            categoryRepository.save(c);
            b.setCategories(info.getCategories());

            questionRepository.save(b);

            return new ResponseEntity<>(b, HttpStatus.CREATED);
        }
        catch (Exception e){
            return new ResponseEntity(HttpStatus.valueOf(400));
        }


    }

    @GetMapping(path="/v1/questions",produces = "application/json")
    public ResponseEntity getAllQuestions() {
        try {
            Authentication authentication =
                    SecurityContextHolder.getContext().getAuthentication();
            //System.out.println(authentication.getName());
            //if(authentication.getName().equals("anonymousUser")) return new ResponseEntity(HttpStatus.valueOf(401));
            UserAccount user = userService.findByEmail(authentication.getName());
            Iterable<Question> questions = questionRepository.findAll();
            // This returns a JSON or XML with the users
            return new ResponseEntity(questions, HttpStatus.valueOf(200));
        }
        catch (Exception e){
            return new ResponseEntity(HttpStatus.valueOf(400));
            }
        }


    @GetMapping(path="/v1/user1/{id}",produces = "application/json")
    public ResponseEntity getUser(@RequestParam String id) {
        //Authentication authentication =
        //        SecurityContextHolder.getContext().getAuthentication();
        //System.out.println(authentication.getName());
        //if(authentication.getName().equals("anonymousUser")) return new ResponseEntity(HttpStatus.valueOf(401));
        //UserAccount user=userService.findByEmail(authentication.getName());
        try {
            UserAccount n = userRepository.findById(id).get();
            return new ResponseEntity(new userInfo_noPwd(n.getId(), n.getFirst_name(), n.getLast_name(), n.getEmailAddress(), n.getAccount_created(), n.getAccount_updated()), HttpStatus.OK);
        }
        catch (Exception e){
            return new ResponseEntity(HttpStatus.valueOf(400));
        }
        }

    @GetMapping(path="/v1/question/{id}",produces = "application/json")
    public ResponseEntity getQestion(@PathVariable String id, @RequestParam String question_id) {
        try {
            Authentication authentication =
                    SecurityContextHolder.getContext().getAuthentication();
            //System.out.println(authentication.getName());
            //if(authentication.getName().equals("anonymousUser")) return new ResponseEntity(HttpStatus.valueOf(401));
            UserAccount user = userService.findByEmail(authentication.getName());

            Optional<Question> question = questionRepository.findById(id);
            if (!question.get().getId().equals(id)) {
                return new ResponseEntity(HttpStatus.NOT_FOUND);
            }

            return new ResponseEntity(question, HttpStatus.OK);
        }
        catch (Exception e){
            return new ResponseEntity(HttpStatus.valueOf(400));
        }
    }

    @DeleteMapping(path="/v1/question/{id}",produces = "application/json")
    public ResponseEntity deleteQuestion(@PathVariable String id) {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        //System.out.println(authentication.getName());
        if(authentication.getName().equals("anonymousUser")) return new ResponseEntity(HttpStatus.valueOf(401));
        UserAccount user=userService.findByEmail(authentication.getName());
        Question question;
        AnswerInfo answer;
        try {
            question = questionRepository.findById(id).get();
            if(!question.getUserId().equals(user.getId())){
                return new ResponseEntity(HttpStatus.NOT_FOUND);
            }
            if(question.getAnswers().size() != 0){
                System.out.println("!answer==null");
                return new ResponseEntity(HttpStatus.NOT_FOUND);
            }
        }
        catch (Exception e){
            return new ResponseEntity(HttpStatus.NOT_FOUND);
        }
        questionRepository.delete(question);

        return new ResponseEntity(HttpStatus.valueOf(204));
    }

    @PutMapping(path="/v1/question/{id}",produces = "application/json")
    public ResponseEntity updateQuestion(@PathVariable String id,@RequestBody QuestionInfo info) {
         Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        //System.out.println(authentication.getName());
        if(authentication.getName().equals("anonymousUser")) return new ResponseEntity(HttpStatus.valueOf(401));
        UserAccount user=userService.findByEmail(authentication.getName());
        Question question;
        try {
            question = questionRepository.findById(id).get();
            if(!question.getUserId().equals(user.getId())){
                return new ResponseEntity(HttpStatus.NOT_FOUND);
            }
        }
        catch (Exception e){
            return new ResponseEntity(HttpStatus.NOT_FOUND);
        }

        question.setQuestion_text(info.getQuestion_text());
        question.setUpdated_timestamp(new Timestamp(System.currentTimeMillis()).toString());

//        for (CategoryInfo categoryInfo : info.getCategories()){
//        question.getCategories().add(info.getCategories());
//        }
//        Set<CategoryInfo> personSet = new TreeSet<>((o1, o2) -> o1.getCategory().compareTo(o2.getCategory()));
//        personSet.addAll(question.getCategories());
//        List<CategoryInfo> result = (List<CategoryInfo>) personSet;

//        for (CategoryInfo categoryInfo : question.getCategories()){
//            if (!info.getCategories().equals(categoryInfo)){
//                categoryRepository.save(categoryInfo);
//            }
//            else {
//                System.out.println("CategoryInfo exist");
//            }
//        }
        CategoryInfo c;
            for (CategoryInfo categoryInfo : info.getCategories()) {
                try {
                    c = categoryRepository.findByCategory(categoryInfo.getCategory()).get();
                }
                catch (Exception e){
                    categoryRepository.save(categoryInfo);
                    question.getCategories().add(categoryInfo);
                }
            }
            questionRepository.save(question);

        return new ResponseEntity(question,HttpStatus.valueOf(200));
    }

    @PostMapping(path="/v1/question/{id}/file",produces = "application/json") // Map ONLY POST Requests
    private ResponseEntity attachFile(@RequestParam("file") MultipartFile file,@PathVariable String id){
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        //System.out.println(authentication.getName());
        if(authentication.getName().equals("anonymousUser")) return new ResponseEntity(HttpStatus.valueOf(401));

        UserAccount user=userService.findByEmail(authentication.getName());

        Question question;
        try {
            question = questionRepository.findById(id).get();
            if(!question.getUserId().equals(user.getId())){
                return new ResponseEntity(HttpStatus.NOT_FOUND);
            }

        }
        catch (Exception e){
            return new ResponseEntity(HttpStatus.NOT_FOUND);
        }

        System.out.println(file.getContentType());
        String contentType= file.getContentType();
        if(!contentType.equals("application/pdf")&&!contentType.equals("image/png")
                && !contentType.equals("image/jpg") && !contentType.equals("image/jpeg")){
            return new ResponseEntity(HttpStatus.BAD_REQUEST);
        }
        List<FileInfo> attachment;
        attachment = new ArrayList<>();
        if(question.getAttachment()!=null||!question.getAttachment().equals(""))
            attachment = question.getAttachment();
        FileInfo fileInfo = fileStorageService.storeFile(file,user.getId(),question.getId());
        attachment.add(fileInfo);
        question.setAttachment(attachment);
        questionRepository.save(question);
        return new ResponseEntity(fileInfo,HttpStatus.valueOf(201));
    }

    @DeleteMapping(path="/v1/question/{questionId}/file/{fileId}",produces = "application/json")
    private ResponseEntity deleteFile(@PathVariable String questionId, @PathVariable String fileId, HttpServletRequest request){
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        //System.out.println(authentication.getName());
        if(authentication.getName().equals("anonymousUser")) return new ResponseEntity(HttpStatus.valueOf(401));

        try{
            UserAccount user=userService.findByEmail(authentication.getName());
            Optional<Question> question=questionRepository.findById(questionId);
            if(!question.get().getUserId().equals(user.getId())){
                return new ResponseEntity(HttpStatus.valueOf(404));
            }
            Optional<FileInfo> fileInfo=fileInfoRepository.findById(fileId);
            fileStorageService.deleteFromS3(fileInfo.get());
            question.get().setAttachment(null);
            questionRepository.save(question.get());
            fileInfoRepository.delete(fileInfo.get());
            // Load file as Resource
            //Resource resource = fileStorageService.loadFileAsResource(fileInfo.get().getFile_name());

            // Try to determine file's content type

            return new ResponseEntity(HttpStatus.NO_CONTENT);
        }catch (Exception ex){
            return new ResponseEntity(HttpStatus.valueOf(404));
        }

    }


//    private boolean QuestionInfoCheck(@RequestBody QuestionInfo info) {
//        if (info.getCategories() == null) {
//            System.out.println("QuestionInfoCheck failed 1");
//            return true;
//        }
//
//        if (info.getQuestion_test().equals("")) {
//            System.out.println("QuestionInfoCheck failed 2");
//            return true;
//        }
//
//        if (info.getCategories().contains("")) {
//            System.out.println("QuestionInfoCheck failed 3");
//            return true;
//        }
//        System.out.println("QuestionInfoCheck successful");
//        return false;
//    }

    @PostMapping(path="/v1/question/{id}/answer",produces = "application/json") // Map ONLY POST Requests
    private ResponseEntity answerQuestion(@RequestParam("question_id") String question_id ,@PathVariable String id, @RequestBody AnswerText answerText){
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        //System.out.println(authentication.getName());
        if(authentication.getName().equals("anonymousUser")) return new ResponseEntity(HttpStatus.valueOf(401));



        UserAccount user=userService.findByEmail(authentication.getName());
        Question question;
        AnswerInfo answerInfo;
        try {
            question = questionRepository.findById(id).get();
            answerInfo = new AnswerInfo();

            if (!question.getUserId().equals(user.getId())) {
                return new ResponseEntity(HttpStatus.NOT_FOUND);
            }


            if (answerText.getAnswer_text() == null || answerText.getAnswer_text().equals(""))
                return new ResponseEntity(HttpStatus.BAD_REQUEST);

            answerInfo.setCreated_timestamp(new Timestamp(System.currentTimeMillis()).toString());
            answerInfo.setUpdated_timestamp(new Timestamp(System.currentTimeMillis()).toString());
            answerInfo.setQuestion_id(id);
            answerInfo.setUserid(user.getId());
            answerInfo.setAnswer_text(answerText.getAnswer_text());
            question.getAnswers().add(answerInfo);



            answerRepository.save(answerInfo);
            questionRepository.save(question);
            return new ResponseEntity(answerInfo, HttpStatus.valueOf(201));
        }
        catch (Exception e){
            return new ResponseEntity(HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping(path="/v1/question/{questionId}/answer/{answerId}",produces = "application/json")
    private ResponseEntity getFile(@PathVariable String questionId, @PathVariable String answerId, HttpServletRequest request){
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        //System.out.println(authentication.getName());
        //if(authentication.getName().equals("anonymousUser")) return new ResponseEntity(HttpStatus.valueOf(401));
        try{
            Optional<AnswerInfo> answerInfos =answerRepository.findById(answerId);
            return ResponseEntity.ok().body(answerInfos.get());
        }catch (Exception e){
            return new ResponseEntity(HttpStatus.valueOf(404));
        }




    }

    @DeleteMapping(path="/v1/question/{questionid}/answer/{answerid}",produces = "application/json")
    private ResponseEntity deleteFile(@RequestParam String question_id, @RequestParam String answer_id, @PathVariable String questionid, @PathVariable String answerid, HttpServletRequest request){
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        //System.out.println(authentication.getName());
        
        if(authentication.getName().equals("anonymousUser")) return new ResponseEntity(HttpStatus.valueOf(401));

        try {
            UserAccount user = userService.findByEmail(authentication.getName());
            Question question = questionRepository.findById(question_id).get();
            if (!question.getUserId().equals(user.getId())) {
                return new ResponseEntity(HttpStatus.valueOf(404));
            }
            Optional<AnswerInfo> answerInfo = answerRepository.findById(answer_id);

            question.getAnswers().remove(answerInfo.get());
            answerRepository.delete(answerInfo.get());
            questionRepository.save(question);
            System.out.println("answer in question deleted");


            return new ResponseEntity(HttpStatus.NO_CONTENT);
        }
        catch (Exception e){
            return new ResponseEntity(HttpStatus.valueOf(400));
        }


    }


    @PutMapping(path="/v1/question/{question_id}/answer/{answer_id}",produces = "application/json")
    private ResponseEntity updateFile(@PathVariable String question_id, @PathVariable String answer_id, @RequestBody AnswerText answer_text, HttpServletRequest request){
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        //System.out.println(authentication.getName());
        if(authentication.getName().equals("anonymousUser")) return new ResponseEntity(HttpStatus.valueOf(401));

        try{
            UserAccount user=userService.findByEmail(authentication.getName());
            Question question=questionRepository.findById(question_id).get();
            if(!question.getUserId().equals(user.getId())){
                return new ResponseEntity(HttpStatus.valueOf(404));
            }
            Optional<AnswerInfo> answerInfo = answerRepository.findById(answer_id);
            answerInfo.get().setAnswer_text(answer_text.getAnswer_text());

            List<AnswerInfo> old = question.getAnswers();
            for (AnswerInfo info : old){
                if (info.getId() == answer_id){
                    info.setAnswer_text(answer_text.getAnswer_text());
                }
            }
            question.setAnswers(old);
            questionRepository.save(question);
            answerRepository.save(answerInfo.get());

            return new ResponseEntity(HttpStatus.NO_CONTENT);
        }catch (Exception ex){
            return new ResponseEntity(HttpStatus.valueOf(404));
        }

    }

    @PostMapping(path="/v1/question/{question_id}/answer/{answer_id}/file/{file_id}",produces = "application/json") // Map ONLY POST Requests
    private ResponseEntity attachFile(@RequestParam("file") MultipartFile file, @PathVariable String question_id, @PathVariable String answer_id, @PathVariable String file_id){
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        //System.out.println(authentication.getName());
        if(authentication.getName().equals("anonymousUser")) return new ResponseEntity(HttpStatus.valueOf(401));

        UserAccount user=userService.findByEmail(authentication.getName());

        Question question;
        AnswerInfo answer;
        try {
            question = questionRepository.findById(question_id).get();
            if(!question.getUserId().equals(user.getId())){
                return new ResponseEntity(HttpStatus.NOT_FOUND);
            }
            answer = answerRepository.findById(answer_id).get();
            if(!answer.getQuestion_id().equals(question.getId())){
                return new ResponseEntity(HttpStatus.NOT_FOUND);


            }

        }
        catch (Exception e){
            return new ResponseEntity(HttpStatus.NOT_FOUND);
        }

        if(question.getAttachment()!=null){
            return new ResponseEntity(HttpStatus.BAD_REQUEST);
        }
        System.out.println(file.getContentType());
        String contentType= file.getContentType();
        if(!contentType.equals("application/pdf")&&!contentType.equals("image/png")
                && !contentType.equals("image/jpg") && !contentType.equals("image/jpeg")){
            return new ResponseEntity(HttpStatus.BAD_REQUEST);
        }
        FileInfo fileInfo = fileStorageService.storeFile(file,user.getId(),answer.getId());
        List<FileInfo> attachment;
        attachment = new ArrayList<>();
        if(question.getAttachment()!=null||!question.getAttachment().equals(""))
            attachment = question.getAttachment();
        attachment.add(fileInfo);
        answer.setAttachment(attachment);
        answerRepository.save(answer);
        return new ResponseEntity(fileInfo,HttpStatus.valueOf(201));
    }

    @DeleteMapping(path="/v1/question/{questionId}/answer/{answerId}/file/{file_id}",produces = "application/json")
    private ResponseEntity deleteFile(@PathVariable String questionId, @PathVariable String answerId, @PathVariable String file_id, HttpServletRequest request){
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        //System.out.println(authentication.getName());
        if(authentication.getName().equals("anonymousUser")) return new ResponseEntity(HttpStatus.valueOf(401));

        try{
            UserAccount user=userService.findByEmail(authentication.getName());
            Optional<Question> question=questionRepository.findById(questionId);
            if(!question.get().getUserId().equals(user.getId())){
                return new ResponseEntity(HttpStatus.valueOf(404));
            }
            Optional<FileInfo> fileInfo=fileInfoRepository.findById(file_id);
            if(!fileInfo.get().equals(question.get().getAttachment())){
                return new ResponseEntity(HttpStatus.valueOf(404));
            }
            fileStorageService.deleteFromS3(fileInfo.get());
            question.get().setAttachment(null);
            questionRepository.save(question.get());
            fileInfoRepository.delete(fileInfo.get());
            // Load file as Resource
            //Resource resource = fileStorageService.loadFileAsResource(fileInfo.get().getFile_name());

            // Try to determine file's content type

            return new ResponseEntity(HttpStatus.NO_CONTENT);
        }catch (Exception ex){
            return new ResponseEntity(HttpStatus.valueOf(404));
        }

    }

}
