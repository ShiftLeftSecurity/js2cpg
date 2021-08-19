var MongoClient = require('mongodb').MongoClient;
var assert = require('assert');
const dbName = 'helloshiftleft';

var url = 'mongodb://localhost:27017/helloshiftleft';

MongoClient.connect(url, function(err, db) {
  assert.equal(null, err);
  console.log("Connected successfully to server");
  db.close();
});

const insertPatients = function(db, callback) {
  const collection = db.collection('patients');
  collection.insertMany([
    {
      _id: 1, 
      patientId: 1, 
      patientFirstName: "Patient", 
      patientLastName:  "Zero",
      dateOfBirth:      "01.01.1970",
      patientWeight:    "75",
      patientHeight:    "1,80",
      medications:      "None",
      body_temp_deg_c:  "50",
      heartRate:        "80",
      pulse_rate:       "100",
      bpDiastolic:      "1"  
    }
  ], function(err, result) {
    callback(result);
  });
};
const insertCustomer = function(db, callback) {
  const collection = db.collection('customers');
  collection.insertMany([
    {
      _id:                    1, 
      clientId:               1, 
      customerId:             1,
      firstName:              "Neo", 
      lastName:               "Morpheus",
      dateOfBirth:            "01.01.1970",
      ssn:                    "75",
      socialInsurancenum:     "1,80",
      tin:                    "None",
      phoneNumber:            "50",
      address:                "80",
      pulse_rate:             "100",
      accounts:               "1"  
    }
  ], function(err, result) {
    callback(result);
  });
};
const findDocuments = function(db, callback) {
  const patientsCollection = db.collection('patients');
  patientsCollection.find({}).toArray(function(err, docs) {
    assert.equal(err, null);
    console.log(docs);
    callback(docs);
  });
  const customerCollection = db.collection('customers');
  customerCollection.find({}).toArray(function(err, docs) {
    assert.equal(err, null);
    console.log(docs);
    callback(docs);
  });
};

MongoClient.connect(url, function(err, client) {
  assert.equal(null, err);
  console.log("[+] Connected successfully to server");
  const db = client.db(dbName);
  console.log("[+] Inserting patients");
  insertPatients(db, function() {
  });
  console.log("[+] Inserting customer");
  insertCustomer(db, function() {
    client.close();
  });
  console.log("[+] done");
});

MongoClient.connect(url, function(err, client) {
  assert.equal(null, err);
  console.log("[+] Connected correctly to server");
  const db = client.db(dbName);
  console.log("[+] Fetching patients");
  findDocuments(db, function() {
    client.close();
  });
  console.log("[+] done");
});