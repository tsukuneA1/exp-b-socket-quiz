build:
	mvn compile -q

server:
	mvn exec:java -Dexec.mainClass=Server

server-port:
	mvn exec:java -Dexec.mainClass=Server -Dexec.args=$(port)

client:
	mvn exec:java -Dexec.mainClass=Client -Dexec.args="$(name)"

client-port:
	mvn exec:java -Dexec.mainClass=Client -Dexec.args="$(name) $(port)"

gui-client:
	mvn exec:java -Dexec.mainClass=GuiClient -Dexec.args="$(name)"

gui-client-port:
	mvn exec:java -Dexec.mainClass=GuiClient -Dexec.args="$(name) $(port)"

test:
	mvn test

fmt:
	mvn spotless:apply

lint:
	mvn spotless:check

observe:
	test -d .venv || python3 -m venv .venv
	.venv/bin/pip install -q -r requirements.txt
	.venv/bin/python3 cli/observe.py
