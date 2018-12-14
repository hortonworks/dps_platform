/*
 *   HORTONWORKS DATAPLANE SERVICE AND ITS CONSTITUENT SERVICES
 *
 *   (c) 2016-2018 Hortonworks, Inc. All rights reserved.
 *
 *   This code is provided to you pursuant to your written agreement with Hortonworks, which may be the terms of the
 *   Affero General Public License version 3 (AGPLv3), or pursuant to a written agreement with a third party authorized
 *   to distribute this code.  If you do not have a written agreement with Hortonworks or with an authorized and
 *   properly licensed third party, you do not have any rights to this code.
 *
 *   If this code is provided to you under the terms of the AGPLv3:
 *   (A) HORTONWORKS PROVIDES THIS CODE TO YOU WITHOUT WARRANTIES OF ANY KIND;
 *   (B) HORTONWORKS DISCLAIMS ANY AND ALL EXPRESS AND IMPLIED WARRANTIES WITH RESPECT TO THIS CODE, INCLUDING BUT NOT
 *     LIMITED TO IMPLIED WARRANTIES OF TITLE, NON-INFRINGEMENT, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE;
 *   (C) HORTONWORKS IS NOT LIABLE TO YOU, AND WILL NOT DEFEND, INDEMNIFY, OR HOLD YOU HARMLESS FOR ANY CLAIMS ARISING
 *     FROM OR RELATED TO THE CODE; AND
 *   (D) WITH RESPECT TO YOUR EXERCISE OF ANY RIGHTS GRANTED TO YOU FOR THE CODE, HORTONWORKS IS NOT LIABLE FOR ANY
 *     DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, PUNITIVE OR CONSEQUENTIAL DAMAGES INCLUDING, BUT NOT LIMITED TO,
 *     DAMAGES RELATED TO LOST REVENUE, LOST PROFITS, LOSS OF INCOME, LOSS OF BUSINESS ADVANTAGE OR UNAVAILABILITY,
 *     OR LOSS OR CORRUPTION OF DATA.
 */

var fs = require('fs');
const Papa = require('papaparse');

const output = fs.createWriteStream('../migrations/locations.sql', {'encoding': 'utf8'});
output.write(`INSERT INTO dataplane.locations
(city, province, country, iso2, latitude, longitude)
VALUES
 `);
let isFirst = true;
var file = 'simplemaps-worldcities-basic.csv';

var content = fs.readFileSync(file, { encoding: 'utf-8' });
Papa.parse(content, {
  header: true,
  skipEmptyLines: true,
  step: rows => {
    rows.data.forEach(cRow => {
      output.write(`${isFirst ? '': ','}('${cRow['city_ascii'].replace(/'/g, "''")}', '${cRow['province'].replace(/'/g, "''")}', '${cRow['country'].replace(/'/g, "''")}', '${cRow['iso2']}', ${cRow['lat']}, ${cRow['lng']})\n`);
      isFirst = false;
    });
  },
  // error: (err, file, inputElem, reason) => console.error(err),
	complete: results =>  {
    output.write(';');
    output.end();
    console.log("All done!");
	}
});