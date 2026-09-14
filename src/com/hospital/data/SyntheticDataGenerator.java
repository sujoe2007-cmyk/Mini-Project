package com.hospital.data;

import com.hospital.model.*;
import com.hospital.routing.Location;
import com.hospital.routing.RoadGraph;
import com.hospital.service.HospitalSystemManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates synthetic baseline data for Nalam District:
 * - 9 Locations in the road network
 * - Road connections with realistic travel times
 * - 1 Primary Hospital and 3 Referral Hospitals
 * - General and ICU Beds
 * - 8 On-duty Doctors with clinical specializations
 * - 22 Synthetic patient admission requests
 */
public class SyntheticDataGenerator {

    public static RoadGraph buildNalamRoadNetwork() {
        RoadGraph graph = new RoadGraph();

        // 9 Locations in Nalam District
        graph.addLocation(new Location("L1", "Nalam Central", "District HQ & NDGH Location"));
        graph.addLocation(new Location("L2", "North Nalam Junction", "Transit Hub"));
        graph.addLocation(new Location("L3", "Vallam Cross", "Sub-district & VSDH Location"));
        graph.addLocation(new Location("L4", "East Highway Circle", "State Highway Connector"));
        graph.addLocation(new Location("L5", "Thoppur Rural", "Agricultural Belt & TCHC Location"));
        graph.addLocation(new Location("L6", "South Bridge", "River Crossing Point"));
        graph.addLocation(new Location("L7", "South Industrial Area", "Industrial Zone Corridor"));
        graph.addLocation(new Location("L8", "Kadur Hills", "Hill Station & KTH Location"));
        graph.addLocation(new Location("L9", "West Bypass", "Ring Road Highway"));

        // Interconnecting road network with travel times (minutes)
        graph.addRoad("L1", "L2", 8);
        graph.addRoad("L1", "L4", 12);
        graph.addRoad("L1", "L6", 10);
        graph.addRoad("L1", "L9", 15);

        graph.addRoad("L2", "L3", 6);   // Path L1 -> L2 -> L3 = 14 mins (VSDH)
        graph.addRoad("L2", "L9", 11);

        graph.addRoad("L4", "L5", 7);   // Path L1 -> L4 -> L5 = 19 mins (TCHC)
        graph.addRoad("L4", "L3", 14);

        graph.addRoad("L6", "L7", 9);
        graph.addRoad("L7", "L8", 11);  // Path L1 -> L6 -> L7 -> L8 = 30 mins (KTH)
        graph.addRoad("L9", "L8", 22);  // Path L1 -> L9 -> L8 = 37 mins (KTH)

        return graph;
    }

    public static Hospital createPrimaryHospital() {
        Hospital ndgh = new Hospital("NDGH", "Nalam District Government Hospital", "L1", true);

        // 2 ICU Beds + 4 General Beds
        ndgh.addBed(new ICUBed("NDGH-ICU-01", "NDGH", "ICU-101", true, true));
        ndgh.addBed(new ICUBed("NDGH-ICU-02", "NDGH", "ICU-102", true, true));
        ndgh.addBed(new GeneralBed("NDGH-GEN-01", "NDGH", "GEN-201", true));
        ndgh.addBed(new GeneralBed("NDGH-GEN-02", "NDGH", "GEN-202", true));
        ndgh.addBed(new GeneralBed("NDGH-GEN-03", "NDGH", "GEN-203", false));
        ndgh.addBed(new GeneralBed("NDGH-GEN-04", "NDGH", "GEN-204", false));

        // 4 Doctors
        ndgh.addDoctor(new Doctor("DOC-01", "Ramesh Kumar", "Critical Care", "NDGH", true));
        ndgh.addDoctor(new Doctor("DOC-02", "Priya Sundaram", "Emergency Medicine", "NDGH", true));
        ndgh.addDoctor(new Doctor("DOC-03", "Anand Varma", "General Medicine", "NDGH", true));
        ndgh.addDoctor(new Doctor("DOC-04", "Kavitha Nair", "General Medicine", "NDGH", true));

        return ndgh;
    }

    public static List<Hospital> createReferralHospitals() {
        List<Hospital> referrals = new ArrayList<>();

        // Referral 1: Vallam Sub-District Hospital (L3, 14 mins from L1)
        Hospital vsdh = new Hospital("VSDH", "Vallam Sub-District Hospital", "L3", false);
        vsdh.addBed(new ICUBed("VSDH-ICU-01", "VSDH", "ICU-301", true, true));
        vsdh.addBed(new ICUBed("VSDH-ICU-02", "VSDH", "ICU-302", false, true));
        vsdh.addBed(new GeneralBed("VSDH-GEN-01", "VSDH", "GEN-301", true));
        vsdh.addBed(new GeneralBed("VSDH-GEN-02", "VSDH", "GEN-302", true));
        vsdh.addBed(new GeneralBed("VSDH-GEN-03", "VSDH", "GEN-303", false));
        vsdh.addDoctor(new Doctor("DOC-05", "Karthik Rajan", "Critical Care", "VSDH", true));
        vsdh.addDoctor(new Doctor("DOC-06", "Meena Krishnan", "General Medicine", "VSDH", true));
        referrals.add(vsdh);

        // Referral 2: Thoppur Community Health Centre (L5, 19 mins from L1)
        Hospital tchc = new Hospital("TCHC", "Thoppur Community Health Centre", "L5", false);
        tchc.addBed(new GeneralBed("TCHC-GEN-01", "TCHC", "GEN-501", true));
        tchc.addBed(new GeneralBed("TCHC-GEN-02", "TCHC", "GEN-502", false));
        tchc.addBed(new GeneralBed("TCHC-GEN-03", "TCHC", "GEN-503", false));
        tchc.addDoctor(new Doctor("DOC-07", "Vignesh Balan", "General Medicine", "TCHC", true));
        referrals.add(tchc);

        // Referral 3: Kadur Taluk Hospital (L8, 30 mins from L1)
        Hospital kth = new Hospital("KTH", "Kadur Taluk Hospital", "L8", false);
        kth.addBed(new ICUBed("KTH-ICU-01", "KTH", "ICU-801", true, true));
        kth.addBed(new ICUBed("KTH-ICU-02", "KTH", "ICU-802", true, false));
        kth.addBed(new GeneralBed("KTH-GEN-01", "KTH", "GEN-801", true));
        kth.addBed(new GeneralBed("KTH-GEN-02", "KTH", "GEN-802", false));
        kth.addDoctor(new Doctor("DOC-08", "Shalini Devi", "Emergency Medicine", "KTH", true));
        referrals.add(kth);

        return referrals;
    }

    public static HospitalSystemManager initializeSystem() {
        RoadGraph graph = buildNalamRoadNetwork();
        Hospital primary = createPrimaryHospital();
        List<Hospital> referrals = createReferralHospitals();
        return new HospitalSystemManager(primary, referrals, graph);
    }

    /**
     * 22 Synthetic patient requests representing a diverse daily intake at Nalam District.
     */
    public static List<SyntheticPatientRecord> getSyntheticPatientBatch() {
        List<SyntheticPatientRecord> list = new ArrayList<>();
        // ID, Name, Age, Gender, Severity, RequiredFacility, Complaint
        list.add(new SyntheticPatientRecord("P-101", "Arun Kumar", 58, "Male", Severity.CRITICAL, "ICU", "Acute Myocardial Infarction / Cardiogenic Shock"));
        list.add(new SyntheticPatientRecord("P-102", "Deepa Mohan", 34, "Female", Severity.SERIOUS, "General", "Post-partum hemorrhage / Severe Anemia"));
        list.add(new SyntheticPatientRecord("P-103", "Murugan Vel", 42, "Male", Severity.MINOR, "General", "Laceration forearm / Suturing required"));
        list.add(new SyntheticPatientRecord("P-104", "Fatima Beevi", 66, "Female", Severity.CRITICAL, "ICU", "Acute Respiratory Failure / ARDS"));
        list.add(new SyntheticPatientRecord("P-105", "Suresh Gopinath", 29, "Male", Severity.SERIOUS, "General", "Road accident / Closed tibia fracture"));
        list.add(new SyntheticPatientRecord("P-106", "Lakshmi Ammal", 71, "Female", Severity.CRITICAL, "ICU", "Severe Sepsis / Septic Shock"));
        list.add(new SyntheticPatientRecord("P-107", "Kavita Sethi", 25, "Female", Severity.MINOR, "General", "Dehydration due to acute gastroenteritis"));
        list.add(new SyntheticPatientRecord("P-108", "Rajesh Khanna", 50, "Male", Severity.SERIOUS, "General", "High grade fever with thrombocytopenia (Dengue)"));
        list.add(new SyntheticPatientRecord("P-109", "Selvamani P.", 63, "Male", Severity.CRITICAL, "ICU", "Cerebrovascular Stroke with coma"));
        list.add(new SyntheticPatientRecord("P-110", "Amina Khatun", 45, "Female", Severity.MINOR, "General", "Mild asthma bronchospasm"));
        list.add(new SyntheticPatientRecord("P-111", "Venkatesh Rao", 52, "Male", Severity.SERIOUS, "General", "Acute pancreatitis"));
        list.add(new SyntheticPatientRecord("P-112", "Chitra Devi", 38, "Female", Severity.CRITICAL, "ICU", "Status epilepticus"));
        list.add(new SyntheticPatientRecord("P-113", "Mani Kandan", 19, "Male", Severity.MINOR, "General", "Second degree minor scald burn"));
        list.add(new SyntheticPatientRecord("P-114", "Sundar Raj", 61, "Male", Severity.SERIOUS, "General", "Bilateral pneumonia requiring high-flow O2"));
        list.add(new SyntheticPatientRecord("P-115", "Shanti Ramasamy", 55, "Female", Severity.CRITICAL, "ICU", "Severe polytrauma with tension pneumothorax"));
        list.add(new SyntheticPatientRecord("P-116", "Praveen Joshua", 31, "Male", Severity.MINOR, "General", "Soft tissue contusion right ankle"));
        list.add(new SyntheticPatientRecord("P-117", "Banumathi K.", 48, "Female", Severity.SERIOUS, "General", "Severe diabetic ketoacidosis"));
        list.add(new SyntheticPatientRecord("P-118", "Dinesh Babu", 36, "Male", Severity.MINOR, "General", "Superficial laceration forehead"));
        list.add(new SyntheticPatientRecord("P-119", "Radhika Narayanan", 67, "Female", Severity.CRITICAL, "ICU", "Pulmonary embolism"));
        list.add(new SyntheticPatientRecord("P-120", "Abdul Kareem", 53, "Male", Severity.SERIOUS, "General", "Acute appendicitis with localized peritonitis"));
        list.add(new SyntheticPatientRecord("P-121", "Nalini Sekar", 27, "Female", Severity.MINOR, "General", "Renal colic / ureteric calculus"));
        list.add(new SyntheticPatientRecord("P-122", "Ravi Shankar", 44, "Male", Severity.SERIOUS, "General", "Blunt abdominal trauma under observation"));
        return list;
    }

    public static class SyntheticPatientRecord {
        public final String id;
        public final String name;
        public final int age;
        public final String gender;
        public final Severity severity;
        public final String requiredFacility;
        public final String complaint;

        public SyntheticPatientRecord(String id, String name, int age, String gender,
                                      Severity severity, String requiredFacility, String complaint) {
            this.id = id;
            this.name = name;
            this.age = age;
            this.gender = gender;
            this.severity = severity;
            this.requiredFacility = requiredFacility;
            this.complaint = complaint;
        }
    }
}
