const express = require('express');
const logger = require('./Logger');

const app = express();
const port = process.env.PORT || 8088;
const SESSION_SECRET_KEY = 'FOO';

app.listen(port, () =>
  logger.log(`Listening on port ${port}!`)
);