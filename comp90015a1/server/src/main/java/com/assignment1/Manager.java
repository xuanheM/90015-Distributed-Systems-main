package com.assignment1;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.assignment1.base.Enum.Command;
import com.assignment1.base.Enum.MessageType;
import com.assignment1.base.Message.C2S.IdentityChange;
import com.assignment1.base.Message.S2C.NewIdentity;
import com.assignment1.base.Message.S2C.RoomChange;


import com.assignment1.base.Message.S2C.NewIdentity;
import com.assignment1.base.Message.S2C.RoomChange;
import com.assignment1.base.Message.S2C.RoomContents;
import com.assignment1.base.Message.S2C.RoomList;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class Manager {

    private ArrayList<ChatRoom> roomList;
    private ArrayList<String> identityList;
    private HashMap<Server.ChatConnection, Guest> guestHashMap;
    private HashMap<Guest, Server.ChatConnection> connectionHashMap;
    private HashMap<String, ChatRoom> roomHashMap;
    private Integer count;

    public Manager() {
        this.roomList = new ArrayList<>();
        ChatRoom hall = new ChatRoom("MainHall");
        this.roomList.add(hall);
        this.identityList = new ArrayList<>();
        this.guestHashMap = new HashMap<>();
        this.connectionHashMap = new HashMap<>();
        this.roomHashMap = new HashMap<>();
        this.roomHashMap.put("MainHall", hall);
        this.count = 0;
    }

    public BroadcastInfo Analyze(String s, Server.ChatConnection connection){
        BroadcastInfo info;
        if(!this.guestHashMap.containsKey(connection)){
            Guest g = new Guest();
            info = NewIdentity(g, connection);
        }
        else{
            JSONObject json = JSON.parseObject(s);
            String command = json.get("type").toString();
            Guest g = this.guestHashMap.get(connection);
            if(command.equals(Command.IDENTITYCHANGE.getCommand())){
                String identity = json.get("identity").toString();
                info = this.IdentityChange(identity, g);
            }
            else if(command.equals(Command.JOIN.getCommand())){
                String roomid = json.get("roomid").toString();
                info = this.Join(roomid, g);
            }
            else if(command.equals(Command.LIST.getCommand())){
                info = this.List(g);
            }
            else if(command.equals(Command.CREATEROOM.getCommand())){
                String roomid = json.get("roomid").toString();
                info = this.CreateRoom(roomid, g);
            }
            else if(command.equals(Command.DELETEROOM.getCommand())){
                String roomid = json.get("roomid").toString();
                info = this.DeleteRoom(roomid, g);
            }
            else if(command.equals(Command.WHO.getCommand())){
                String roomid = json.get("roomid").toString();
                info = this.Who(roomid, g);
            }
            else if(command.equals(Command.QUIT.getCommand())){
                info = this.Quit(g);
            }
            else{
                info = null;
            }
        }
        return info;
    }

    private synchronized BroadcastInfo NewIdentity(Guest g, Server.ChatConnection connection){
        g.setIdentity("guest" + this.count.toString());
        this.count += 1;
        g.setCurrentRoom("MainHall");
        this.roomHashMap.get("MainHall").addMember(g);
        this.connectionHashMap.put(g, connection);
        this.guestHashMap.put(connection, g);
        this.identityList.add(g.getIdentity());
        NewIdentity ni = new NewIdentity();
        ni.setType(MessageType.NEWIDENTITY.getType());
        ni.setFormer("");
        ni.setIdentity(g.getIdentity());
        BroadcastInfo info = new BroadcastInfo();
        info.setContent(JSON.toJSONString(ni));
        info.addConnection(connection);
        return info;
    }

    private synchronized BroadcastInfo IdentityChange(String identity, Guest g){
        String pattern = "[a-zA-Z0-9]{3,16}";
        String defaultPattern = "guest[0-9]{0,11}";
        NewIdentity ni = new NewIdentity();
        ni.setType(MessageType.NEWIDENTITY.getType());
        ni.setFormer(g.getIdentity());
        if(Pattern.matches(pattern, identity) &&
                !Pattern.matches(defaultPattern, identity) &&
                !this.identityList.contains(identity)){
            g.setIdentity(identity);
            ni.setIdentity(identity);
        }
        else{
            ni.setIdentity(g.getIdentity());
        }
        BroadcastInfo info = new BroadcastInfo();
        info.setContent(JSON.toJSONString(ni));
        ArrayList<Guest> guestsToSend = this.roomHashMap.get(g.getCurrentRoom()).getMembers();
        for(int i=0;i<guestsToSend.size();i++){
            info.addConnection(this.connectionHashMap.get(guestsToSend.get(i)));
        }
        return info;
    }

    private synchronized BroadcastInfo Join(String roomid, Guest g){
        BroadcastInfo info = new BroadcastInfo();

        RoomChange roomchange = new RoomChange();
        roomchange.setType(MessageType.ROOMCHANGE.getType());
        roomchange.setIdentity(g.getIdentity());
        roomchange.setFormer(g.getCurrentRoom());
        //已经在要加入的房间或者加入的房间在roomlist里不存在
        if(g.getCurrentRoom().equals(roomid)|| !this.roomHashMap.containsKey(roomid)){
            //啥也不变
            roomchange.setRoomid(g.getCurrentRoom());
            info.setContent(JSON.toJSONString(roomchange));
            //只对当前用户发送RoomChange Message
            info.addConnection(this.connectionHashMap.get(g));
        }
        else{
            //当前房间删人，目标房间加人
            this.roomHashMap.get(g.getCurrentRoom()).deleteMember(g);
            this.roomHashMap.get(roomid).addMember(g);
            g.setCurrentRoom(roomid);
            roomchange.setRoomid(roomid);
            info.setContent(JSON.toJSONString(roomchange));
            //对目标房间内的人发送声明有人加入
            ArrayList<Guest> guestsToSend = this.roomHashMap.get(roomid).getMembers();
            for(int i=0; i< guestsToSend.size();i++){
                info.addConnection(this.connectionHashMap.get(guestsToSend.get(i)));
            }
        }
        return info;
    }

    private synchronized BroadcastInfo List(Guest g){
        return null;
    }

    private synchronized BroadcastInfo CreateRoom(String roomid, Guest g){
        BroadcastInfo info = new BroadcastInfo();
        String pattern = "^[a-zA-Z][0-9]{3,32}";

        RoomChange createRoom = new RoomChange();
        createRoom.setType(Command.CREATEROOM.getCommand());
        createRoom.setRoomid(roomid);

        //如果房间名符合要求且不存在
        if(Pattern.matches(pattern, roomid)&&!this.roomHashMap.containsKey(roomid)){
            ChatRoom newRoom = new ChatRoom(roomid);
            newRoom.setOwner(g);
            newRoom.setRoomid(roomid);
            this.roomList.add(newRoom);
            this.roomHashMap.put(roomid, newRoom);
            info.setContent(JSON.toJSONString(createRoom));
            info.addConnection(this.connectionHashMap.get(g));
        }
        else {
            info.setContent(JSON.toJSONString(createRoom));
        }

        return info;
    }

    private synchronized BroadcastInfo DeleteRoom(String roomid, Guest g){
        BroadcastInfo info = new BroadcastInfo();
        RoomChange deleteRoom = new RoomChange();
        deleteRoom.setRoomid(roomid);
        deleteRoom.setType(Command.DELETEROOM.getCommand());

        //roomList中包含要删除的room, 且guest是要删除的room owner
        if(this.roomHashMap.containsKey(roomid)&&this.roomHashMap.get(roomid).getOwner().equals(g)){
            this.roomList.remove(this.roomHashMap.get(roomid));
            this.roomHashMap.remove(roomid);
            info.setContent(JSON.toJSONString(deleteRoom));
            info.addConnection(this.connectionHashMap.get(g));
        }
        else{
            info.setContent(JSON.toJSONString(deleteRoom));
        }
        return info;
    }

    private synchronized BroadcastInfo Who(String roomid, Guest g){
        BroadcastInfo info = new BroadcastInfo();
        RoomContents who = new RoomContents();

        who.setType(MessageType.ROOMCONTENTS.getType());
        who.setRoomid(roomid);
        ArrayList<Guest> guestsInRoom = this.roomHashMap.get(g.getCurrentRoom()).getMembers();
        ArrayList<String> guestsIdentity = new ArrayList<>();
        //把房间里的所有client identity存入 一个string arraylist 然后放入who.setIdentities().
        for(int i =0; i < guestsInRoom.size();i++){
            guestsIdentity.add(guestsInRoom.get(i).getIdentity());
        }
        who.setIdentities(guestsIdentity);
        who.setOwner(this.roomHashMap.get(roomid).getOwner().getIdentity());
        info.setContent(JSON.toJSONString(who));
        info.addConnection(this.connectionHashMap.get(g));
        //如果房间为mainhall, owner应为空 ++
        return info;
    }

    private synchronized BroadcastInfo Quit(Guest g){
        BroadcastInfo info = new BroadcastInfo();
        RoomChange quit = new RoomChange();
        quit.setType(Command.QUIT.getCommand());
        info.setContent(JSON.toJSONString(quit));

        ArrayList<Guest> guestsToSend = this.roomHashMap.get(g.getCurrentRoom()).getMembers();


        //如果退出的用户创建过房间，找到并删除
        for(String roomid: this.roomHashMap.keySet()){
            if(g.equals(this.roomHashMap.get(roomid).getOwner())){
                this.roomList.remove(this.roomHashMap.get(roomid));
                this.roomHashMap.remove(roomid);
            }
            this.identityList.remove(g.getIdentity());
            this.connectionHashMap.remove(g);
            this.guestHashMap.remove(g);

        for(int i =0; i<guestsToSend.size(); i++){
            info.addConnection(this.connectionHashMap.get(guestsToSend.get(i)));
        }

        }

        return info;
    }

    class BroadcastInfo{

        private String content;
        private ArrayList<Server.ChatConnection> connections;

        public BroadcastInfo(){
            this.connections = new ArrayList<>();
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public ArrayList<Server.ChatConnection> getConnections() {
            return connections;
        }

        public void addConnection(Server.ChatConnection connect) {
            this.connections.add(connect);
        }

        public void deleteConnection(Server.ChatConnection connect){
            this.connections.remove(connect);
        }
    }
}
