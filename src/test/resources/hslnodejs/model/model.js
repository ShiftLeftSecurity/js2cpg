const MongoClient = require('mongodb').MongoClient;
const assert = require('assert');

// Connection URL
const url = 'mongodb://localhost:27017';

// Database Name
const dbName = 'myproject';


const findDocuments = function(id, db) {

  const collection = db.collection('documents');

  collection.find(id).toArray(function(err, docs) {
    console.log("Found the following records");
    
    console.log(docs);
    return docs; 
  });
};


exports.find = function (id) {
	result = null; 
	// Use connect method to connect to the server
	MongoClient.connect(url, function(err, client) 
		{
			if ((client !== undefined) && (client !== null)) {
				console.log("Connected correctly to server");
				const db = client.db(dbName);
				result = findDocuments(id, db);
				console.log(result);
			} else {
				console.log("Could not connect to the server.Is mongodb running?");
			}
		}
	);
	return result;
};

