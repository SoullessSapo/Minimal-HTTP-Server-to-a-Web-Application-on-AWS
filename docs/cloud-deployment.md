# Deploying the application to the cloud

The deployment runs **the same source code** that runs locally. What changes is the host, the
network boundary and the environment variables — never the application.

The platform used here is **AWS EC2**: one Linux instance, the jar installed under
`/opt/application-server` and supervised by systemd.

> **Before starting.** Use the AWS account, region and instance type approved by the instructor. A
> running instance can generate charges. Never commit a private key, an account identifier or any
> credential; the scripts read them from your environment.

---

## 1. Prepare the artifact

```bash
mvn -B clean package
PORT=8080 APP_ENV=production java -jar target/application-server.jar
./scripts/smoke-test.sh http://localhost:8080 disabled
```

- The build produces `target/application-server.jar`, a single executable file that already
  contains the page, the style sheet, the script and the image.
- The runtime required is **Java 17 or newer**.
- The framework binds the wildcard address, so the application is reachable from outside the host
  and not only from its loopback interface.

## 2. Launch the instance

1. Launch **one** Linux instance (Amazon Linux 2023 or Ubuntu) of the smallest approved type.
2. Keep the default VPC and a public subnet unless the instructor provides another network.
3. Give it a descriptive name tag, for example `web-framework-lab`.
4. Choose the approved connection method: Session Manager, EC2 Instance Connect or SSH.

## 3. Security group

| Direction | Port | Source | Why |
| --- | --- | --- | --- |
| Inbound | 8080 | the range allowed by the instructor, or `0.0.0.0/0` for a classroom test | the application |
| Inbound | 22 | **your own public address** | administration, only when SSH is the method |
| Outbound | all | default | installing the Java runtime |

Nothing else has to be opened. The application speaks plain HTTP: TLS is out of the scope of this
laboratory.

## 4. Install and start

From your computer:

```bash
export EC2_HOST=ec2-user@ec2-XX-XX-XX-XX.compute-1.amazonaws.com
export EC2_KEY=~/.ssh/your-lab-key.pem      # kept outside the repository
export APP_PORT=8080
export APP_ENV=production                   # this is what keeps /shutdown unpublished
export GREETING_PREFIX=Hello
./scripts/deploy-to-ec2.sh
```

The script builds and tests the artifact, uploads the jar together with
`scripts/install-on-instance.sh` and `scripts/application-server.service`, and runs the installer
on the instance. The installer:

1. installs a headless Java 17 runtime;
2. creates the unprivileged `appuser` account and `/opt/application-server`;
3. writes `/etc/systemd/system/application-server.service` with `PORT`, `APP_ENV` and
   `GREETING_PREFIX` as environment variables;
4. enables and starts the service;
5. verifies `/pi`, `/config` and that `/shutdown` answers **404**.

On Windows PowerShell, or when the connection method is Session Manager, upload the same three
files and run on the instance:

```bash
sudo bash install-on-instance.sh application-server.jar 8080 production Hello
```

## 5. Verify

From the instance:

```bash
curl -s http://localhost:8080/config
systemctl show application-server -p Environment
journalctl -u application-server -f
```

From your computer:

```bash
curl -i "http://<public-dns>:8080/hello?name=Pedro"
curl -i http://<public-dns>:8080/pi
curl -i http://<public-dns>:8080/shutdown          # must answer 404
./scripts/smoke-test.sh http://<public-dns>:8080 disabled
```

Then open `http://<public-dns>:8080/` in a browser with the network view visible: the page, the
style sheet, the script and the image must load from the cloud, and the buttons must call the
services without reloading.

## 6. Changing configuration without rebuilding

The greeting prefix, the port and the environment are variables of the service unit, so changing
one is an operation, not a new build:

```bash
sudo systemctl edit application-server      # or edit the unit file directly
# [Service]
# Environment=GREETING_PREFIX=Hola
sudo systemctl restart application-server
curl -s "http://<public-dns>:8080/hello?name=Pedro"     # Hola Pedro
```

## 7. Mandatory cleanup

A forgotten instance keeps generating charges even when nobody uses it.

```bash
sudo systemctl stop application-server      # 1. stop the application
```

2. Save the logs and screenshots needed for the submission.
3. Terminate the instance and confirm that its state becomes **terminated**.
4. Release any Elastic IP that was allocated.
5. Delete the laboratory security group once nothing uses it.
6. Check the cost or billing view of the account.

## Official references

- [Launch an Amazon EC2 instance](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/EC2_GetStarted.html)
- [Connect to a Linux instance using SSH](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/connect-linux-inst-ssh.html)
- [Security group rules for common use cases](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/security-group-rules-reference.html)
- [systemd.service — unit configuration](https://www.freedesktop.org/software/systemd/man/systemd.service.html)
