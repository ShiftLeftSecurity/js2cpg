exports.customerHandler = function (req, res) {
	 fs.readFile('testfile.txt', function(err, data) {
	    res.writeHead(200, {'Content-Type': 'text/html'});
	    res.write(data);
	    res.end();
	  });
};

exports.customerHandler()