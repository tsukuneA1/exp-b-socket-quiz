build:
	mvn compile -q

server:
	mvn exec:java -Dexec.mainClass=apps.Server

server-port:
	mvn exec:java -Dexec.mainClass=apps.Server -Dexec.args=$(port)

client:
	mvn exec:java -Dexec.mainClass=apps.Client -Dexec.args="$(name)"

client-port:
	mvn exec:java -Dexec.mainClass=apps.Client -Dexec.args="$(name) $(port)"

test:
	mvn test

fmt:
	mvn spotless:apply

lint:
	mvn spotless:check
