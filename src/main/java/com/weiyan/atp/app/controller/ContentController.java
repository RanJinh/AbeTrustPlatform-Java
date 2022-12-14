package com.weiyan.atp.app.controller;

import com.weiyan.atp.constant.BaseException;
import com.weiyan.atp.data.bean.ChaincodeResponse;
import com.weiyan.atp.data.bean.DABEUser;
import com.weiyan.atp.data.bean.Result;
import com.weiyan.atp.data.request.web.DecryptContentRequest;
import com.weiyan.atp.data.request.web.RevokeUserAttrRequest;
import com.weiyan.atp.data.request.web.ShareContentRequest;
import com.weiyan.atp.data.request.web.UploadFileRequest;
import com.weiyan.atp.data.response.intergration.DecryptionResponse;
import com.weiyan.atp.data.response.intergration.EncryptionResponse;
import com.weiyan.atp.data.response.web.PlatContentsResponse;
import com.weiyan.atp.service.AttrService;
import com.weiyan.atp.service.ContentService;

import com.weiyan.atp.service.DABEService;
import com.weiyan.atp.utils.JsonProviderHolder;
import com.weiyan.atp.utils.SecurityUtils;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.FileCopyUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * @author : ??????thor
 * @since : 2020/6/10
 */
@RestController
@RequestMapping("/content")
@Slf4j
@CrossOrigin //??????????????????
public class ContentController {
    private final ContentService contentService;
    private final AttrService attrService;

    private final DABEService dabeService;

    @Value("${atp.devMode.baseUrl}")
    private String baseUrl;

    @Value("${atp.path.shareData}")
    private String shareDataPath;

    @Value("${atp.path.encryptData}")
    private String encryptDataPath;

    @Value("${atp.path.cipherData}")
    private String cipherDataPath;

    @Value("${atp.path.dabeUser}")
    private String userPath;

    private static int cnt = 0;

    public ContentController(ContentService contentService, AttrService attrService,DABEService dabeService) {
        this.contentService = contentService;
        this.attrService = attrService;
        this.dabeService = dabeService;
    }

    @PostMapping("/")
    public Result<Object> encryptContent(@RequestBody @Validated ShareContentRequest request) {
        int tagSize = request.getTags().size();
        if (tagSize == 0 || tagSize > 10) {
            return Result.internalError("tags length error");
        }
        EncryptionResponse encryptionResponse = contentService.encContent(request);

//        //??????
//        String url = baseUrl+"/attrpolicy";
//        HttpClient client = HttpClients.createDefault();
//        //??????post??????
//        HttpPost post = new HttpPost(url);
//        //???????????????
//        JSONObject json = new JSONObject();
//        JSONArray array = new JSONArray();
//        try {
//            json.put("contentHash", encryptionResponse.getCipherHash());
//            json.put("policy",request.getPolicy());
//            json.put("uid",request.getFileName());
//            array.put(request.getTags().get(0));
//            array.put(request.getTags().get(1));
//            array.put(request.getTags().get(2));
//            array.put(request.getTags().get(3));
//            json.put("tags",array);
//            json.put("timestamp",encryptionResponse.getTimeStamp());
//            String message = "[" + json + "]";
//            post.addHeader("Content-type", "application/json; charset=utf-8");
//            post.setHeader("Accept", "application/json");
//            post.setEntity(new StringEntity(message, StandardCharsets.UTF_8));
//            HttpResponse httpResponse = client.execute(post);
//            HttpEntity entity = httpResponse.getEntity();
//            System.err.println("??????:" + httpResponse.getStatusLine());
//            System.err.println("??????:" + EntityUtils.toString(entity));
//
//        } catch (JSONException e) {
//            e.printStackTrace();
//        } catch (ClientProtocolException e) {
//            e.printStackTrace();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        return Result.success();
    }

//    @PostMapping("/")
    @PostMapping("/share")
    public Result<Object> shareContent(@RequestBody @Validated ShareContentRequest request) {
        int tagSize = request.getTags().size();
        if (tagSize == 0 || tagSize > 10) {
            return Result.internalError("tags length error");
        }
        contentService.shareContent(request);
        return Result.success();
    }

    @PostMapping("/decryption")
    public Result<String> decryptContent(@RequestBody @Validated DecryptContentRequest request,HttpServletRequest req) {
        String ipAddress = SecurityUtils.getIpAddr(req);
        request.setIp(ipAddress);
        ChaincodeResponse response = null;
        if(!request.getUserName().equals(request.getSharedUser())){

            String filePath = encryptDataPath +request.getSharedUser()+"/"+ request.getFileName();
            try {
                String cipher= FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8);
                request.setCipher(cipher);
            } catch (IOException e) {
                e.printStackTrace();
            }
            response = contentService.decryptContent2(request.getCipher(), request.getUserName(), request.getFileName(), request.getSharedUser());
        }else{
            response = new ChaincodeResponse();
            response.setStatus(ChaincodeResponse.Status.SUCCESS);
        }

        //??????
        String url = baseUrl+"/share_judgement";
        HttpClient client = HttpClients.createDefault();
        //??????post??????
        HttpPost post = new HttpPost(url);
        //???????????????
        JSONObject json = new JSONObject();
        JSONArray array = new JSONArray();
        try {
            if(response.isFailed()){
                json.put("result","false");
            }else{
                json.put("result","true");
            }
            json.put("contentHash", SecurityUtils.md5(request.getCipher()));
            String filePath = userPath + request.getUserName();
            String resource = FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8);
            DABEUser user = JsonProviderHolder.JACKSON.parse(resource, DABEUser.class);
            json.put("userChannel",user.getChannel());
            json.put("uid",request.getUserName());
            json.put("userIP",request.getIp());
            array.put(request.getTags().get(0));
            array.put(request.getTags().get(1));
            array.put(request.getTags().get(2));
            array.put(request.getTags().get(3));
            json.put("tags",array);
            json.put("timestamp",new Date().toString());
            String message = "[" + json + "]";
            post.addHeader("Content-type", "application/json; charset=utf-8");
            post.setHeader("Accept", "application/json");
            post.setEntity(new StringEntity(message, StandardCharsets.UTF_8));
            HttpResponse httpResponse = client.execute(post);
            HttpEntity entity = httpResponse.getEntity();
            System.err.println("??????:" + httpResponse.getStatusLine());
            System.err.println("??????:" + EntityUtils.toString(entity));

        } catch (JSONException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(response.isFailed()){
            throw new BaseException("decryption error: " + response.getMessage());
        }

        if(cnt>=5){
            revokeUserAttr(request.getUserName(),"??????????????????");
            attrService.syncSuccessAttrApply(request.getUserName());
        }
        cnt++;
        return Result.okWithData(null);
    }

    //@PostMapping("/decryption")
    @PostMapping("/decrypt")
    public Result<String> decContent(@RequestBody @Validated DecryptContentRequest request) {
        return Result.okWithData(
                contentService.decryptContent(request.getCipher(), request.getFileName()));
    }

    @GetMapping("/list")
    public Result<PlatContentsResponse> queryContents(String fromUserName, String tag,
                                                      int pageSize, String bookmark) {
        PlatContentsResponse res = contentService.queryPlatContents(fromUserName, tag, pageSize, bookmark);
        return Result.okWithData(res);
    }

    @PostMapping("/testUpload")
    public Result<Object> encryptFile(@RequestBody @Validated ShareContentRequest request) throws IOException {
        //String fileNameHash = SecurityUtils.md5(request.getSharedfileName());
        String data = FileUtils.readFileToString(
                new File("atp/test/"+request.getSharedFileName()),
                StandardCharsets.UTF_8);
        FileUtils.write(new File(shareDataPath +request.getFileName()+"/"+ request.getSharedFileName()), data,
                StandardCharsets.UTF_8);
        request.setPlainContent(data);
        EncryptionResponse encryptionResponse = contentService.encContent2(request);

        FileUtils.write(new File(encryptDataPath +request.getFileName()+"/"+ request.getSharedFileName()), encryptionResponse.getCipher(),
                StandardCharsets.UTF_8);
        return Result.success();
    }

    @PostMapping("/upload")
    public Result<Object> upload(@ModelAttribute @Validated ShareContentRequest request,HttpServletRequest req) throws IOException {
        MultipartFile file = request.getFile();
        if(file.isEmpty()){
            return Result.internalError("file is empty");
        }
//        String ipAddress = SecurityUtils.getIpAddr(req);
////        request.setIp(ipAddress);
        if(request.getTags().get(2).equals("test")){
            request.setIp("101.201.49.180");
        }else{
            request.setIp("58.247.201.96");
        }
        //????????????????????????
        String filename = file.getOriginalFilename();
        System.out.println(filename);
        //????????????????????????????????????
        File dest = new File(new File(shareDataPath).getAbsolutePath()+ "/" + request.getFileName()+"/"+filename);
        System.out.println(dest.getPath());
        if (!dest.getParentFile().exists()) {
            dest.getParentFile().mkdir();
        }
        FileUtils.copyInputStreamToFile(file.getInputStream(), dest);
        //file.transferTo(dest);
        //FileCopyUtils.copy(file.getInputStream(),new FileOutputStream(dest));
        String data = FileUtils.readFileToString(
                dest,
                StandardCharsets.UTF_8);
        request.setPlainContent(data);
        request.setSharedFileName(filename);
        EncryptionResponse encryptionResponse = contentService.encContent2(request);

        FileUtils.write(new File(encryptDataPath +request.getFileName()+"/"+ filename), encryptionResponse.getCipher(),
                StandardCharsets.UTF_8);

        //??????
        String url = baseUrl+"/attrpolicy";
        HttpClient client = HttpClients.createDefault();
        //??????post??????
        HttpPost post = new HttpPost(url);
        //???????????????
        JSONObject json = new JSONObject();
        JSONArray array = new JSONArray();
        try {
            json.put("contentHash", SecurityUtils.md5(encryptionResponse.getCipherHash()));
            json.put("policy",request.getPolicy());
            json.put("uid",request.getFileName());
            array.put(request.getTags().get(0));
            array.put(request.getTags().get(1));
            array.put(request.getTags().get(2));
            array.put(request.getTags().get(3));
            json.put("tags",array);
            json.put("userIP",request.getIp());
            json.put("timestamp",encryptionResponse.getTimeStamp());
            String message = "[" + json + "]";
            post.addHeader("Content-type", "application/json; charset=utf-8");
            post.setHeader("Accept", "application/json");
            post.setEntity(new StringEntity(message, StandardCharsets.UTF_8));
            HttpResponse httpResponse = client.execute(post);
            HttpEntity entity = httpResponse.getEntity();
            System.err.println("??????:" + httpResponse.getStatusLine());
            System.err.println("??????:" + EntityUtils.toString(entity));

        } catch (JSONException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if(request.getTags().get(2).equals("test")){
            revokeUserAttr(request.getFileName(),"????????????");
            attrService.syncSuccessAttrApply(request.getFileName());
        }
        return Result.success();
    }

    //????????????????????????
    @GetMapping("/download")
    public  void download(String fileName, String sharedUser,HttpServletRequest request, HttpServletResponse response) throws Exception {
        //???????????????????????????
        File dest = new File(new File(shareDataPath).getAbsolutePath()+ "/" + sharedUser+"/"+fileName);
        //??????????????????????????????????????????
        FileInputStream fis = new FileInputStream(dest);
        //?????????????????????.txt???
        String extendFileName = fileName.substring(fileName.lastIndexOf('.'));
        //???????????????????????????????????????????????????????????????????????????
        //response.setContentType(request.getSession().getServletContext().getMimeType(extendFileName));
        //response.setContentType("content-type:octet-stream");
        response.setContentType("application/force-download");
        //???????????????,attachment?????????????????????????????????inline??????????????????
        response.setHeader("content-disposition","attachment;fileName="+ URLEncoder.encode(fileName,"UTF-8"));
        //??????????????????????????????????????????
        ServletOutputStream os = response.getOutputStream();
        //????????????,??????spring????????????FileCopyUtils??????
        FileCopyUtils.copy(fis,os);
        //FileUtils.copyFile(dest,os);
        //return Result.success();
    }

    //????????????
    @GetMapping("/cipher")
    public  void cipher(String userName,String fileName, String sharedUser,HttpServletRequest request, HttpServletResponse response) throws Exception {
        ChaincodeResponse resp = contentService.getCipher(userName, fileName, sharedUser);
        if(resp.isFailed()){
            throw new BaseException("download cipher error: " + resp.getMessage());
        }
        //????????????
        FileUtils.write(new File(cipherDataPath +sharedUser+"/"+ fileName), resp.getMessage(),
                StandardCharsets.UTF_8);

        //???????????????????????????
        File dest = new File(new File(cipherDataPath).getAbsolutePath()+ "/" + sharedUser+"/"+fileName);
        //??????????????????????????????????????????
        FileInputStream fis = new FileInputStream(dest);
        //?????????????????????.txt???
        //String extendFileName = fileName.substring(fileName.lastIndexOf('.'));
        //???????????????????????????????????????????????????????????????????????????
        //response.setContentType(request.getSession().getServletContext().getMimeType(extendFileName));
        //response.setContentType("content-type:octet-stream");
        response.setContentType("application/force-download");
        //???????????????,attachment?????????????????????????????????inline??????????????????
        response.setHeader("content-disposition","attachment;fileName="+ URLEncoder.encode(fileName,"UTF-8"));
        //??????????????????????????????????????????
        ServletOutputStream os = response.getOutputStream();
        //????????????,??????spring????????????FileCopyUtils??????
        FileCopyUtils.copy(fis,os);
        //FileUtils.copyFile(dest,os);
        //return Result.success();
    }


    //????????????????????????
    public void revokeUserAttr(String userName,String msg) {
        DABEUser user = dabeService.getUser(userName);
        for(String attrName:user.getAppliedAttrMap().keySet()){
            RevokeUserAttrRequest request = new RevokeUserAttrRequest();
            request.setToUserName(userName);
            request.setAttrName(attrName);
            String owner = attrName.split(":")[0];
            request.setUserName(owner);
            request.setRemark(msg);
            ChaincodeResponse chaincodeResponse = attrService.revokeAttr(request);
            //??????
            String url = baseUrl+"/addattr";
            HttpClient client = HttpClients.createDefault();
            //??????post??????
            HttpPost post = new HttpPost(url);
            //???????????????
            JSONObject json = new JSONObject();
            try {

                json.put("result","revocation");
                json.put("channel_name", user.getChannel());
                json.put("fromUserName",request.getUserName());
                //json.put("fromOrgName",request.getToOrgName());
                json.put("toUserName",request.getToUserName());
                json.put("attrName",request.getAttrName());
                json.put("timestamp",new Date().toString());
                String message = "[" + json + "]";
                post.addHeader("Content-type", "application/json; charset=utf-8");
                post.setHeader("Accept", "application/json");
                post.setEntity(new StringEntity(message, StandardCharsets.UTF_8));
                HttpResponse httpResponse = client.execute(post);
                HttpEntity entity = httpResponse.getEntity();
                System.err.println("??????:" + httpResponse.getStatusLine());
                System.err.println("??????:" + EntityUtils.toString(entity));

            } catch (JSONException e) {
                e.printStackTrace();
            } catch (ClientProtocolException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
