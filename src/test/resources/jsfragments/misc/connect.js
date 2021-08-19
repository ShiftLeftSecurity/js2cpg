var MongoClient = require('mongodb').MongoClient
    , assert = require('assert');
const dbName = 'helloshiftleft';
// Connection URL
var url = 'mongodb://localhost:27017/helloshiftleft';

// Use connect method to connect to the server
MongoClient.connect(url, function(err, db) {
    assert.equal(null, err);
    console.log("Connected successfully to server");

    db.close();
});
