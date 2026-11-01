package com.hospital.api;

import com.hospital.data.DatabaseManager;
import com.hospital.model.*;
import com.hospital.routing.*;
import com.hospital.service.HospitalSystemManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;

/**
 * Embedded HTTP REST and Static File Server (JDK com.sun.net.httpserver).
 * Enables genuine End-to-End communication:
 * Web UI -> HTTP Server API -> HospitalSystemManager (Collections + Algorithms) -> MySQL (JDBC)
 */
public class HospitalApiServer {

    private final HospitalSystemManager manager;
    private final int port;
    private final PortalAuthService auth = new PortalAuthService();
    private HttpServer server;

    public HospitalApiServer(HospitalSystemManager manager, int port) {
        this.manager = manager;
        this.port = port;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/status", new StatusHandler());
        server.createContext("/api/auth/signup", new AuthHandler());
        server.createContext("/api/auth/login", new AuthHandler());
        server.createContext("/api/auth/logout", new AuthHandler());
        server.createContext("/api/hospitals", managementOnly(new HospitalsHandler()));
        server.createContext("/api/availability", new AvailabilityHandler());
        server.createContext("/api/patient", new PatientDetailHandler());
        server.createContext("/api/patient/me", new PatientDetailHandler());
        server.createContext("/api/patients", managementOnly(new PatientsHandler()));
        server.createContext("/api/queue", managementOnly(new QueueHandler()));
        server.createContext("/api/register", new RegisterHandler());
        server.createContext("/api/process-queue", managementOnly(new ProcessQueueHandler()));
        server.createContext("/api/discharge", managementOnly(new DischargeHandler()));
        server.createContext("/api/doctor-duty", managementOnly(new DoctorDutyHandler()));
        server.createContext("/api/patient-severity", managementOnly(new PatientSeverityHandler()));
        server.createContext("/api/dijkstra", managementOnly(new DijkstraHandler()));
        server.createContext("/api/audit", managementOnly(new AuditHandler()));
        server.createContext("/api/batch", managementOnly(new BatchHandler()));
        server.createContext("/", new StaticFileHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("[HospitalApiServer] HTTP REST Server listening on http://localhost:" + port + "/");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String bearer(HttpExchange x) {
        String value=x.getRequestHeaders().getFirst("Authorization");
        return value!=null && value.startsWith("Bearer ") ? value.substring(7) : null;
    }
    private PortalAuthService.Account requireRole(HttpExchange x, String role) throws IOException {
        PortalAuthService.Account account=auth.session(bearer(x));
        if(account==null) { sendJsonResponse(x,401,"{\"success\":false,\"error\":\"Please sign in to continue.\"}"); return null; }
        if(!role.equals(account.role)) { sendJsonResponse(x,403,"{\"success\":false,\"error\":\"This action is not available for your account type.\"}"); return null; }
        return account;
    }
    private HttpHandler managementOnly(HttpHandler delegate) {
        return x -> {
            if("OPTIONS".equalsIgnoreCase(x.getRequestMethod())) { addCorsHeaders(x); x.sendResponseHeaders(204,-1); return; }
            if(requireRole(x,"MANAGEMENT")==null)return;
            delegate.handle(x);
        };
    }

    private class AuthHandler implements HttpHandler {
        public void handle(HttpExchange x) throws IOException {
            addCorsHeaders(x);
            if("OPTIONS".equalsIgnoreCase(x.getRequestMethod())) { x.sendResponseHeaders(204,-1); return; }
            String path=x.getRequestURI().getPath();
            if(path.endsWith("/logout")) { auth.logout(bearer(x)); sendJsonResponse(x,200,"{\"success\":true}"); return; }
            if(!"POST".equalsIgnoreCase(x.getRequestMethod())) { x.sendResponseHeaders(405,-1); return; }
            try {
                Map<String,String> p=parseSimpleJson(readRequestBody(x));
                PortalAuthService.Account a=path.endsWith("/signup")
                    ? auth.signup(p.get("name"),p.get("email"),p.get("password"),p.get("role"))
                    : auth.login(p.getOrDefault("email",""),p.getOrDefault("password",""));
                sendJsonResponse(x,200,"{\"success\":true,\"token\":\""+a.token+"\",\"email\":\""+escapeJson(a.email)+"\",\"name\":\""+escapeJson(a.name)+"\",\"role\":\""+a.role+"\",\"patientId\":"+jsonStringOrNull(a.patientId)+"}");
            } catch(Exception e) {
                if(e instanceof SQLException && ((SQLException)e).getSQLState()!=null && ((SQLException)e).getSQLState().startsWith("23"))
                    sendJsonResponse(x,409,"{\"success\":false,\"error\":\"An account with this email already exists. Sign in instead.\"}");
                else sendJsonResponse(x,e instanceof IllegalArgumentException?400:503,errorJson(e));
            }
        }
    }

    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            boolean dbAvail = DatabaseManager.isDatabaseAvailable();
            String json = String.format("{\"status\":\"OK\",\"databaseConnected\":%b,\"databaseUrl\":\"%s\"}",
                    dbAvail, escapeJson(DatabaseManager.getJdbcUrl()));
            sendJsonResponse(exchange, 200, json);
        }
    }

    private class HospitalsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            StringBuilder sb = new StringBuilder("[");
            boolean firstH = true;
            for (Hospital h : manager.getAllHospitals()) {
                if (!firstH) sb.append(",");
                firstH = false;
                sb.append("{");
                sb.append("\"id\":\"").append(h.getHospitalId()).append("\",");
                sb.append("\"name\":\"").append(escapeJson(h.getName())).append("\",");
                sb.append("\"locationId\":\"").append(h.getLocationId()).append("\",");
                sb.append("\"isPrimary\":").append(h.isPrimary()).append(",");
                sb.append("\"totalBeds\":").append(h.getTotalBedsCount()).append(",");
                sb.append("\"availableBeds\":").append(h.getAvailableBedsCount()).append(",");
                sb.append("\"occupiedBeds\":").append(h.getOccupiedBedsCount()).append(",");

                // Beds array
                sb.append("\"beds\":[");
                boolean firstB = true;
                for (Bed b : h.getAllBeds()) {
                    if (!firstB) sb.append(",");
                    firstB = false;
                    sb.append("{");
                    sb.append("\"id\":\"").append(b.getBedId()).append("\",");
                    sb.append("\"number\":\"").append(b.getBedNumber()).append("\",");
                    sb.append("\"type\":\"").append(b.getBedType()).append("\",");
                    sb.append("\"isOccupied\":").append(b.isOccupied()).append(",");
                    sb.append("\"patientId\":").append(jsonStringOrNull(b.getCurrentPatientId()));
                    sb.append("}");
                }
                sb.append("],");

                // Doctors array
                sb.append("\"doctors\":[");
                boolean firstD = true;
                for (Doctor d : h.getAllDoctors()) {
                    if (!firstD) sb.append(",");
                    firstD = false;
                    sb.append("{");
                    sb.append("\"id\":\"").append(d.getDoctorId()).append("\",");
                    sb.append("\"name\":\"").append(escapeJson(d.getName())).append("\",");
                    sb.append("\"specialization\":\"").append(escapeJson(d.getSpecialization())).append("\",");
                    sb.append("\"isOnDuty\":").append(d.isOnDuty()).append(",");
                    sb.append("\"assignedPatientId\":").append(jsonStringOrNull(d.getAssignedPatientId()));
                    sb.append("}");
                }
                sb.append("]");

                sb.append("}");
            }
            sb.append("]");
            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    private class PatientsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Patient p : manager.getAllPatients().values()) {
                if (!first) sb.append(",");
                first = false;
                sb.append(patientToJson(p));
            }
            sb.append("]");
            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    private class PatientDetailHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            PortalAuthService.Account account=requireRole(exchange,"PATIENT");
            if(account==null)return;
            String id=null;
            try(java.sql.Connection c=DatabaseManager.getConnection(); java.sql.PreparedStatement p=c.prepareStatement("SELECT patient_id FROM portal_accounts WHERE email=?")) {
                p.setString(1,account.email); try(java.sql.ResultSet r=p.executeQuery()){if(r.next())id=r.getString(1);}
            } catch(Exception e) { sendJsonResponse(exchange,503,errorJson(e)); return; }
            if(id==null) { sendJsonResponse(exchange,200,"{\"registered\":false}"); return; }
            Patient patient = id == null ? null : manager.getPatient(id);
            if (patient == null) {
                sendJsonResponse(exchange, 404, "{\"success\":false,\"error\":\"Patient record not found.\"}");
                return;
            }
            sendJsonResponse(exchange, 200, patientToJson(patient));
        }
    }

    private class AvailabilityHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            for (Hospital hospital : manager.getAllHospitals()) {
                if (!first) json.append(',');
                first = false;
                long general = hospital.getAvailableBedsCount(GeneralBed.class);
                long icu = hospital.getAvailableBedsCount(ICUBed.class);
                json.append("{\"id\":\"").append(escapeJson(hospital.getHospitalId())).append("\",")
                    .append("\"name\":\"").append(escapeJson(hospital.getName())).append("\",")
                    .append("\"locationId\":\"").append(escapeJson(hospital.getLocationId())).append("\",")
                    .append("\"availableGeneralBeds\":").append(general).append(',')
                    .append("\"availableIcuBeds\":").append(icu).append(',')
                    .append("\"availableBeds\":").append(general + icu).append(',')
                    .append("\"availableDoctors\":").append(hospital.getAvailableDoctorCount()).append('}');
            }
            json.append(']');
            sendJsonResponse(exchange, 200, json.toString());
        }
    }

    private class QueueHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            List<Patient> snapshot = manager.getWaitingListSnapshot();
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Patient p : snapshot) {
                if (!first) sb.append(",");
                first = false;
                sb.append(patientToJson(p));
            }
            sb.append("]");
            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    private class RegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            try {
                Map<String, String> params = parseSimpleJson(readRequestBody(exchange));
                PortalAuthService.Account account=auth.session(bearer(exchange));
                if(account==null || !("PATIENT".equals(account.role)||"MANAGEMENT".equals(account.role))) {
                    sendJsonResponse(exchange,401,"{\"success\":false,\"error\":\"Sign in before submitting a patient registration.\"}"); return;
                }
                boolean self="PATIENT".equals(account.role);
                if(self && account.patientId!=null) { sendJsonResponse(exchange,409,"{\"success\":false,\"error\":\"A patient registration already exists for this account.\"}"); return; }
                String id = params.get("id");
                if (self || id == null || id.trim().isEmpty()) {
                    do {
                        id = "NAL-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
                    } while (manager.getPatient(id) != null);
                }
                String name = self ? account.name : params.get("name");
                int age = Integer.parseInt(params.getOrDefault("age", "30"));
                String gender = params.getOrDefault("gender", "Unspecified");
                String severityValue = params.getOrDefault("severity", "MINOR");
                if (severityValue == null) severityValue = "MINOR";
                Severity severity = Severity.valueOf(severityValue.toUpperCase(Locale.ROOT));
                String facility = params.getOrDefault("requiredFacility", "General");

                if(self) auth.attachPatient(account.email,id);
                Patient p;
                try { p = manager.registerPatient(id, name, age, gender, severity, facility); }
                catch(Exception e) { if(self) try { auth.clearPatient(account.email,id); } catch(SQLException cleanup) { e.addSuppressed(cleanup); } throw e; }
                if(self) manager.processWaitingQueue();
                sendJsonResponse(exchange, 201, "{\"success\":true,\"databasePersisted\":" +
                        DatabaseManager.isDatabaseAvailable() + ",\"patient\":" + patientToJson(p) + "}");
            } catch (Exception ex) {
                int status = ex instanceof IllegalArgumentException ? 400 : 500;
                sendJsonResponse(exchange, status, errorJson(ex));
            }
        }
    }

    private class ProcessQueueHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            try {
                List<AdmissionRequest> results = manager.processWaitingQueue();
                StringBuilder sb = new StringBuilder("{\"success\":true,\"databasePersisted\":")
                        .append(DatabaseManager.isDatabaseAvailable())
                        .append(",\"count\":").append(results.size()).append(",\"results\":[");
                boolean first = true;
                for (AdmissionRequest r : results) {
                    if (!first) sb.append(",");
                    first = false;
                    sb.append("{");
                    sb.append("\"requestId\":\"").append(escapeJson(r.getRequestId())).append("\",");
                    sb.append("\"patientId\":\"").append(escapeJson(r.getPatient().getPatientId())).append("\",");
                    sb.append("\"disposition\":\"").append(r.getDisposition()).append("\",");
                    sb.append("\"allocatedHospitalId\":").append(jsonStringOrNull(r.getAllocatedHospitalId())).append(",");
                    sb.append("\"allocatedBedId\":").append(jsonStringOrNull(r.getAllocatedBedId())).append(",");
                    sb.append("\"allocatedDoctorId\":").append(jsonStringOrNull(r.getAllocatedDoctorId())).append(",");
                    sb.append("\"travelTime\":").append(r.getTravelTimeMinutes()).append(",");
                    sb.append("\"remarks\":\"").append(escapeJson(r.getRemarks())).append("\"");
                    sb.append("}");
                }
                sb.append("]}");
                sendJsonResponse(exchange, 200, sb.toString());
            } catch (RuntimeException ex) {
                sendJsonResponse(exchange, 500, errorJson(ex));
            }
        }
    }

    private class DischargeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            try {
                Map<String, String> params = parseSimpleJson(readRequestBody(exchange));
                String hospitalId = params.get("hospitalId");
                String patientId = params.get("patientId");
                boolean ok = manager.dischargePatient(hospitalId, patientId);
                if (ok) {
                    sendJsonResponse(exchange, 200, "{\"success\":true,\"databasePersisted\":" +
                            DatabaseManager.isDatabaseAvailable() + ",\"message\":\"Patient discharged successfully\"}");
                } else {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"error\":\"Discharge failed or patient not found\"}");
                }
            } catch (RuntimeException ex) {
                sendJsonResponse(exchange, 500, errorJson(ex));
            }
        }
    }

    private class DoctorDutyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            try {
                Map<String, String> params = parseSimpleJson(readRequestBody(exchange));
                String hospitalId = params.get("hospitalId");
                String doctorId = params.get("doctorId");
                String dutyValue = params.getOrDefault("onDuty", "true");
                if (dutyValue == null || (!"true".equalsIgnoreCase(dutyValue) && !"false".equalsIgnoreCase(dutyValue))) {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"error\":\"onDuty must be true or false.\"}");
                    return;
                }
                boolean onDuty = Boolean.parseBoolean(dutyValue);
                boolean ok = manager.setDoctorDutyStatus(hospitalId, doctorId, onDuty);
                sendJsonResponse(exchange, ok ? 200 : 400, "{\"success\":" + ok +
                        ",\"databasePersisted\":" + DatabaseManager.isDatabaseAvailable() + "}");
            } catch (RuntimeException ex) {
                sendJsonResponse(exchange, 500, errorJson(ex));
            }
        }
    }

    private class PatientSeverityHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            try {
                Map<String, String> params = parseSimpleJson(readRequestBody(exchange));
                Severity severity = Severity.valueOf(params.getOrDefault("severity", "").toUpperCase(Locale.ROOT));
                boolean updated = manager.updatePatientSeverity(params.get("patientId"), severity);
                sendJsonResponse(exchange, updated ? 200 : 404, "{\"success\":" + updated +
                        ",\"databasePersisted\":" + DatabaseManager.isDatabaseAvailable() + "}");
            } catch (IllegalArgumentException ex) {
                sendJsonResponse(exchange, 400, errorJson(ex));
            } catch (RuntimeException ex) {
                sendJsonResponse(exchange, 500, errorJson(ex));
            }
        }
    }

    private class DijkstraHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            RoutePlanner planner = new RoutePlanner();
            DijkstraResult res = planner.calculateShortestPaths(manager.getRoadGraph(), "L1");

            StringBuilder sb = new StringBuilder("{");
            sb.append("\"source\":\"L1\",");
            sb.append("\"routes\":[");
            boolean first = true;
            for (Hospital h : manager.getAllHospitals()) {
                if (h.isPrimary()) continue;
                if (!first) sb.append(",");
                first = false;
                sb.append("{");
                sb.append("\"hospitalId\":\"").append(h.getHospitalId()).append("\",");
                sb.append("\"name\":\"").append(escapeJson(h.getName())).append("\",");
                sb.append("\"locationId\":\"").append(h.getLocationId()).append("\",");
                sb.append("\"travelTime\":").append(res.getDistanceTo(h.getLocationId())).append(",");
                sb.append("\"path\":[");
                List<String> path = res.getPathTo(h.getLocationId());
                for (int i = 0; i < path.size(); i++) {
                    sb.append("\"").append(path.get(i)).append("\"");
                    if (i < path.size() - 1) sb.append(",");
                }
                sb.append("]}");
            }
            sb.append("]}");
            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    private class AuditHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            List<AuditRecord> list = manager.getAuditHistory();
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (AuditRecord r : list) {
                if (!first) sb.append(",");
                first = false;
                sb.append("{");
                sb.append("\"seq\":").append(r.getSequence()).append(",");
                sb.append("\"type\":\"").append(r.getEventType()).append("\",");
                sb.append("\"patientId\":").append(jsonStringOrNull(r.getPatientId())).append(",");
                sb.append("\"hospitalId\":").append(jsonStringOrNull(r.getHospitalId())).append(",");
                sb.append("\"bedId\":").append(jsonStringOrNull(r.getBedId())).append(",");
                sb.append("\"doctorId\":").append(jsonStringOrNull(r.getDoctorId())).append(",");
                sb.append("\"details\":\"").append(escapeJson(r.getDetails())).append("\",");
                sb.append("\"timestamp\":\"").append(r.getTimestamp()).append("\"");
                sb.append("}");
            }
            sb.append("]");
            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    private class BatchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            try {
                var batch = com.hospital.data.SyntheticDataGenerator.getSyntheticPatientBatch();
                for (var rec : batch) {
                    if (manager.getPatient(rec.id) != null) {
                        sendJsonResponse(exchange, 409, "{\"success\":false,\"error\":\"Batch patient already exists: " +
                                escapeJson(rec.id) + "\"}");
                        return;
                    }
                }
                for (var rec : batch) {
                    manager.registerPatient(rec.id, rec.name, rec.age, rec.gender, rec.severity, rec.requiredFacility);
                }
                sendJsonResponse(exchange, 200, "{\"success\":true,\"databasePersisted\":" +
                        DatabaseManager.isDatabaseAvailable() + ",\"ingested\":" + batch.size() + "}");
            } catch (RuntimeException ex) {
                sendJsonResponse(exchange, 500, errorJson(ex));
            }
        }
    }

    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if ("/".equals(path) || path.isEmpty()) {
                path = "/index.html";
            }
            File file = new File("web", path);
            if (!file.exists() || file.isDirectory()) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            String contentType = "text/plain";
            if (path.endsWith(".html")) contentType = "text/html; charset=utf-8";
            else if (path.endsWith(".css")) contentType = "text/css; charset=utf-8";
            else if (path.endsWith(".js")) contentType = "application/javascript; charset=utf-8";
            else if (path.endsWith(".json")) contentType = "application/json; charset=utf-8";
            else if (path.endsWith(".svg")) contentType = "image/svg+xml";

            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[1024];
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return baos.toString(StandardCharsets.UTF_8);
        }
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> values = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) return values;
        for (String part : rawQuery.split("&")) {
            String[] pair = part.split("=", 2);
            String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            String value = pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
            values.put(key, value);
        }
        return values;
    }

    private Map<String, String> parseSimpleJson(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("A JSON request body is required.");
        }
        JsonCursor cursor = new JsonCursor(json);
        cursor.expect('{');
        cursor.skipWhitespace();
        if (cursor.consume('}')) return map;
        do {
            String key = cursor.readString();
            cursor.skipWhitespace();
            cursor.expect(':');
            cursor.skipWhitespace();
            String value = cursor.peek() == '"' ? cursor.readString() : cursor.readPrimitive();
            map.put(key, "null".equals(value) ? null : value);
            cursor.skipWhitespace();
            if (cursor.consume('}')) break;
            cursor.expect(',');
            cursor.skipWhitespace();
        } while (true);
        cursor.skipWhitespace();
        if (!cursor.isAtEnd()) {
            throw new IllegalArgumentException("Unexpected content after JSON object.");
        }
        return map;
    }

    private final class JsonCursor {
        private final String input;
        private int index;

        JsonCursor(String input) {
            this.input = input;
        }

        void skipWhitespace() {
            while (!isAtEnd() && Character.isWhitespace(input.charAt(index))) index++;
        }

        boolean consume(char expected) {
            if (!isAtEnd() && input.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        void expect(char expected) {
            if (!consume(expected)) {
                throw new IllegalArgumentException("Malformed JSON request body.");
            }
        }

        char peek() {
            if (isAtEnd()) {
                throw new IllegalArgumentException("Malformed JSON request body.");
            }
            return input.charAt(index);
        }

        boolean isAtEnd() {
            return index >= input.length();
        }

        String readString() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (!isAtEnd()) {
                char current = input.charAt(index++);
                if (current == '"') return value.toString();
                if (current != '\\') {
                    if (current < 0x20) throw new IllegalArgumentException("Invalid control character in JSON string.");
                    value.append(current);
                    continue;
                }
                if (isAtEnd()) break;
                char escaped = input.charAt(index++);
                switch (escaped) {
                    case '"', '\\', '/' -> value.append(escaped);
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'u' -> {
                        if (index + 4 > input.length()) throw new IllegalArgumentException("Invalid Unicode escape in JSON string.");
                        try {
                            value.append((char) Integer.parseInt(input.substring(index, index + 4), 16));
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("Invalid Unicode escape in JSON string.", e);
                        }
                        index += 4;
                    }
                    default -> throw new IllegalArgumentException("Invalid escape in JSON string.");
                }
            }
            throw new IllegalArgumentException("Unterminated JSON string.");
        }

        String readPrimitive() {
            int start = index;
            while (!isAtEnd() && input.charAt(index) != ',' && input.charAt(index) != '}') index++;
            String value = input.substring(start, index).trim();
            if (value.isEmpty()) throw new IllegalArgumentException("Missing JSON value.");
            return value;
        }
    }

    private String patientToJson(Patient p) {
        return "{" +
                "\"registered\":true," +
                "\"id\":\"" + escapeJson(p.getPatientId()) + "\"," +
                "\"name\":\"" + escapeJson(p.getName()) + "\"," +
                "\"age\":" + p.getAge() + "," +
                "\"gender\":\"" + escapeJson(p.getGender()) + "\"," +
                "\"severity\":\"" + p.getSeverity() + "\"," +
                "\"requiredFacility\":\"" + escapeJson(p.getRequiredFacility()) + "\"," +
                "\"arrivalSequence\":" + p.getArrivalSequence() + "," +
                "\"status\":\"" + p.getStatus() + "\"," +
                "\"assignedHospitalId\":" + jsonStringOrNull(p.getAssignedHospitalId()) + "," +
                "\"assignedBedId\":" + jsonStringOrNull(p.getAssignedBedId()) + "," +
                "\"assignedDoctorId\":" + jsonStringOrNull(p.getAssignedDoctorId()) + "," +
                "\"referralNotes\":" + jsonStringOrNull(p.getReferralNotes()) +
                "}";
    }

    private String jsonStringOrNull(String value) {
        return value == null ? "null" : "\"" + escapeJson(value) + "\"";
    }

    private String errorJson(Exception error) {
        String message = error.getMessage();
        return "{\"success\":false,\"error\":\"" + escapeJson(message != null ? message : "Request failed.") + "\"}";
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        StringBuilder escaped = new StringBuilder(str.length());
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) escaped.append(String.format("\\u%04x", (int) c));
                    else escaped.append(c);
                }
            }
        }
        return escaped.toString();
    }
}
