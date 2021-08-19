var exec = require("child_process").exec;
var url = require('url');
exports.getSearch = function (req, res) {
    res.writeHead(200, {'Content-Type': 'text/html'});
 	var param = url.parse(req.url, true);
	  //executes my shell script - main.sh when a request is posted to the server
	    try {
    	  exec(param.query.q, function (err, stdout, stderr) {

		    if (err){
	            console.log("\n"+stderr);
			    res.end();
		    }

		    //Print stdout/stderr to console
		    console.log(stdout);
		    console.log(stderr);

		    //Simple response to user whenever localhost:8888 is accessed
		    res.write(stdout);
		    res.end();
		  });
		} catch (e) {
		   	console.log(e); // pass exception object to error handler
			res.write("Something went wrong");
			res.end();
		}
};

exports.postSearch = function (req, res) {
    res.writeHead(200, {'Content-Type': 'text/html'});
	res.write("No Post");
	res.end();
};