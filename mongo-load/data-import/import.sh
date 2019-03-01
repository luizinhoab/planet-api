#!/usr/bin/env bash
nohup mongod  &
sleep 2

mongo --disableImplicitSessions << EOF
    use admin
    db.createUser({
      user: "admin",
      pwd: "4dm1n",
      roles: [
              { role: "userAdminAnyDatabase", db: "admin" },
              { role: "readWriteAnyDatabase", db: "admin" },
              { role: "dbAdminAnyDatabase", db: "admin" },
              { role: "clusterAdmin", db: "admin" }
      ]
    });
EOF
mongod --dbpath /data/db/ --shutdown

nohup mongod --bind_ip_all --port 27016 --auth &
sleep 2

cd /data-import
pattern="./"
folderpattern="/"
initpattern="data-import"
for D in `find . -type d`
do
   folder=${D#$pattern}
   echo "Reading folder ${folder}"
   ls -1 ${folder}/*.json | sed 's/.json$//' | while read col; do
        filename=${col#$folder}
        echo "Read folder ${folder#initpattern} and file .${filename}.json"
	      mongoimport --host 127.0.0.1 --port 27016 -u admin -p 4dm1n --authenticationDatabase admin --db ${folder#initpattern} --collection ${filename#$folderpattern} --type json --file ${col}.json --jsonArray
   done
   sleep 2
   mongo --host 127.0.0.1 --port 27016 -u admin -p 4dm1n --authenticationDatabase admin --disableImplicitSessions <<EOF
      use ${folder};
      db.createUser({
        user: "tester",
        pwd: "t3st3r",
        roles: [{role:"readWrite", db:"${folder}"}]
      });
EOF
done
sleep infinity
