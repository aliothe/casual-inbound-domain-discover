# casual-inbound-domain-discover
Connect and issue domain discovery towards host:port to see if it is working as expected

# Running
CASUAL_DOMAIN_NAME=testing-inbound CASUAL_CONFIG_FILE=./etc/casual-config.json java -jar  src/build/libs/casual-domain-discovery-${jar-version}.jar localhost 7772

# On success
Exit code 0

# On failure
Logs the problem and exits with code -1
