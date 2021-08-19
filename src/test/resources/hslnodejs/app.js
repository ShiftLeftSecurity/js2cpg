var http = require('http');
var url = require('url');
var fs = require('fs');
var express = require('express');
var cookieParser = require('cookie-parser');


var app = express();
app.use(cookieParser());

const MongoClient = require('mongodb').MongoClient;


var customer = require('./modules/customer');
var search = require('./modules/search');
var patient = require('./modules/patient');
var index = require('./modules/index');


// Connection URL
const dburl = 'mongodb://localhost:27017';

// Database Name
const dbName = 'helloshiftleft';
var db;

MongoClient.connect(dburl, (err, database) => {
  if (err) return console.log(err);
  db = database.db(dbName);
});


// customer GET request
app.get('/', function (req, res) {
	index.getIndex(req, res);
});

// customer GET request
app.post('/', function (req, res) {
	index.getIndex(req, res);
});


// customer GET request
app.get('/customer', function (req, res) {
	customer.customerHandler(req, res);
});

// RCE 
app.get('/search', function (req, res) {
	search.getSearch(req, res);
});

// POST 
app.post('/search', function (req, res) {
	search.postSearch(req, res);
});

// patient 
app.get('/patient/:id', function (req, res) {
	patient.getPatient(req, res, db);

});

// patient 
app.post('/patient/:id', function (req, res) {
	patient.postPatient(req, res, db);

});

var server = app.listen(8081, function () {
   var host = server.address().address;
   var port = server.address().port;
   console.log("Example app listening at http://%s:%s", host, port);
});