package com.hospital.api;

import com.hospital.data.DatabaseManager;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Database-backed portal accounts with short-lived in-process bearer sessions. */
final class PortalAuthService {
    static final class Account {
        final String email, name, role, patientId, token;
        Account(String email, String name, String role, String patientId) { this(email,name,role,patientId,null); }
        Account(String email, String name, String role, String patientId, String token) {
            this.email=email; this.name=name; this.role=role; this.patientId=patientId; this.token=token;
        }
        Map<String,Object> json(String token) {
            Map<String,Object> v=new LinkedHashMap<>(); v.put("token",this.token); v.put("email",email);
            v.put("name",name); v.put("role",role); v.put("patientId",patientId); return v;
        }
    }
    private static final SecureRandom RANDOM=new SecureRandom();
    private static final Map<String,Account> SESSIONS=new ConcurrentHashMap<>();

    Account signup(String name, String email, String password, String role) throws Exception {
        if (!DatabaseManager.isDatabaseAvailable()) throw new SQLException("Database is unavailable. Start MySQL and verify db.properties before creating an account.");
        if (name==null || name.trim().isEmpty() || email==null || !email.matches("(?i)^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || password==null || password.length()<8 || !("PATIENT".equals(role)||"MANAGEMENT".equals(role)))
            throw new IllegalArgumentException("Enter a name, valid email, password of at least 8 characters, and account type.");
        byte[] salt=new byte[16]; RANDOM.nextBytes(salt);
        String sql="INSERT INTO portal_accounts(email,full_name,password_salt,password_hash,role) VALUES(?,?,?,?,?)";
        try(Connection c=DatabaseManager.getConnection(); PreparedStatement p=c.prepareStatement(sql)) {
            p.setString(1,email.trim().toLowerCase(Locale.ROOT)); p.setString(2,name.trim()); p.setString(3,hex(salt));
            p.setString(4,hash(password,salt)); p.setString(5,role); p.executeUpdate();
        }
        return login(email,password);
    }
    Account login(String email, String password) throws Exception {
        if (!DatabaseManager.isDatabaseAvailable()) throw new SQLException("Database is unavailable. Start MySQL and try again.");
        String sql="SELECT email,full_name,password_salt,password_hash,role,patient_id FROM portal_accounts WHERE email=?";
        try(Connection c=DatabaseManager.getConnection(); PreparedStatement p=c.prepareStatement(sql)) {
            p.setString(1,email.trim().toLowerCase(Locale.ROOT));
            try(ResultSet r=p.executeQuery()) {
                if(!r.next()) throw new IllegalArgumentException("No account found for this email. Create an account first.");
                byte[] salt=unhex(r.getString("password_salt"));
                if(!MessageDigest.isEqual(hash(password,salt).getBytes(java.nio.charset.StandardCharsets.US_ASCII),r.getString("password_hash").getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
                    throw new IllegalArgumentException("Incorrect email or password.");
                Account a=new Account(r.getString("email"),r.getString("full_name"),r.getString("role"),r.getString("patient_id"));
                String token=UUID.randomUUID().toString()+UUID.randomUUID().toString();
                Account sessionAccount=new Account(a.email,a.name,a.role,a.patientId,token); SESSIONS.put(token,sessionAccount); return sessionAccount;
            }
        }
    }
    Account session(String token) { return token==null?null:SESSIONS.get(token); }
    void logout(String token) { if(token!=null)SESSIONS.remove(token); }
    void attachPatient(String email,String id) throws SQLException {
        try(Connection c=DatabaseManager.getConnection(); PreparedStatement p=c.prepareStatement("UPDATE portal_accounts SET patient_id=? WHERE email=? AND role='PATIENT' AND patient_id IS NULL")) {
            p.setString(1,id); p.setString(2,email); if(p.executeUpdate()!=1) throw new SQLException("This account already has a patient registration.");
        }
    }
    void clearPatient(String email,String id) throws SQLException {
        try(Connection c=DatabaseManager.getConnection(); PreparedStatement p=c.prepareStatement("UPDATE portal_accounts SET patient_id=NULL WHERE email=? AND patient_id=?")) { p.setString(1,email); p.setString(2,id); p.executeUpdate(); }
    }
    private static String hash(String p,byte[] salt) throws Exception { PBEKeySpec spec=new PBEKeySpec(p.toCharArray(),salt,210000,256); try{return hex(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());} finally{spec.clearPassword();} }
    private static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format("%02x",x));return s.toString();}
    private static byte[] unhex(String s){byte[] b=new byte[s.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return b;}
}
