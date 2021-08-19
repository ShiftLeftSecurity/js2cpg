var db = require('../model/model');
var url = require('url');

exports.getPatient = function (req, res, db, collectionName) {
    res.writeHead(200, {'Content-Type': 'text/html'});
	var cursor = db.collection("patients").find({ "a": { $gt: Number(req.params.id)}}).toArray(function(err, results) {
	  	for (var i = 0; i < results.length; i++) {
 	   		res.write(String(results[i]["a"]));
		}
		res.end();
	})
};

exports.postPatient = function (req, res) {
    res.writeHead(200, {'Content-Type': 'text/html'});
	res.write("No Post");
	res.end();
};