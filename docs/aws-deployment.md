# Deploying the application to one AWS EC2 instance

The application deployed on EC2 is exactly the application tested locally. What changes is the
host and the network boundary, not the architecture: the same jar, the same sequential accept loop,
the same hardcoded services.

> **Before starting.** Use the AWS account, region, Linux image, instance type and connection
> method approved by the instructor. A running EC2 instance can generate charges. Never commit a
> private key, an account identifier or any credential to this repository; if one is ever
> committed, notify the instructor and revoke it immediately.

---

## 1. Prepare the artifact

```bash
mvn -B clean package
java -jar target/minimal-http-server.jar --port 35000   # test the packaged artifact locally
./scripts/smoke-test.sh http://localhost:35000
```

- The build produces `target/minimal-http-server.jar`, a single executable file that already
  contains the HTML page, the style sheet, the client script and both images.
- The runtime required is **Java 17 or newer**.
- The listening port is configurable with `--port` or with the `PORT` environment variable.
- The server binds the wildcard address, so it accepts connections from outside the instance and
  not only from its loopback interface.

## 2. Launch the instance

1. Open the EC2 console and launch **one** Linux instance using the course approved image
   (Amazon Linux 2023 or Ubuntu) and the smallest approved instance type.
2. Keep the default VPC and a public subnet unless the instructor provides a different network.
3. Give it a descriptive name tag, for example `tdse-lab2-http-server`.
4. Choose the approved connection method: Session Manager, EC2 Instance Connect or SSH.

## 3. Security group

The security group is the instance level firewall. Open only what the laboratory needs:

| Direction | Port | Source | Why |
| --- | --- | --- | --- |
| Inbound | 22 (SSH) | **your current public IP only** | administration, only when SSH is the approved method |
| Inbound | 35000 (TCP) | the range allowed by the instructor, or `0.0.0.0/0` for a short classroom test | the application itself |
| Outbound | all | default | package installation |

Do not expose port 22 to every address. If the connection method is Session Manager, no
administration port has to be opened at all.

## 4. Install and start

With SSH available, everything is driven from your computer:

```bash
export EC2_HOST=ec2-user@ec2-XX-XX-XX-XX.compute-1.amazonaws.com
export EC2_KEY=~/.ssh/your-lab-key.pem     # kept outside the repository
export APP_PORT=35000
./scripts/deploy-to-ec2.sh
```

The script builds the artifact, uploads the jar together with
`scripts/install-on-instance.sh` and `scripts/minimal-http-server.service`, and runs the installer
on the instance. The installer:

1. installs a headless Java 17 runtime with `dnf` or `apt-get`;
2. creates the unprivileged `appuser` account and `/opt/minimal-http-server`;
3. writes `/etc/systemd/system/minimal-http-server.service` with the chosen port;
4. enables and starts the service;
5. verifies `http://localhost:35000/health` **from inside the instance**.

With Session Manager, upload the same three files through the console or `aws ssm`, then run:

```bash
sudo bash install-on-instance.sh minimal-http-server.jar 35000
```

## 5. Verify

From inside the instance:

```bash
curl -i http://localhost:35000/health
systemctl status minimal-http-server
journalctl -u minimal-http-server -f
```

From your computer:

```bash
curl -i http://<public-dns>:35000/health
./scripts/smoke-test.sh http://<public-dns>:35000
```

Then open `http://<public-dns>:35000/` in a browser: the page, its style sheet, its script and both
images have to load from EC2, and the three services have to answer through the public address.

## 6. Run after logout

The application runs as a systemd service, so it:

- starts on its own after `systemctl enable --now`, and again after a reboot;
- writes its logs to the journal (`journalctl -u minimal-http-server`);
- stops cleanly with `sudo systemctl stop minimal-http-server`, because the shutdown hook of the
  application releases the listening socket when it receives SIGTERM;
- keeps running when the administration session is closed.

## 7. Mandatory cleanup

A forgotten instance keeps generating charges even when nobody is using it.

```bash
sudo systemctl stop minimal-http-server     # 1. stop the application
```

2. Save only the logs and screenshots needed for the submission.
3. Terminate the EC2 instance and confirm that its state becomes **terminated**.
4. Release the Elastic IP if one was allocated.
5. Delete the laboratory security group once no instance uses it.
6. Check the cost or billing view available in the learner account.

## Official AWS references

- [Launch an Amazon EC2 instance](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/EC2_GetStarted.html)
- [Connect to an EC2 instance](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/AccessingInstance.html)
- [Connect to a Linux instance using SSH](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/connect-linux-inst-ssh.html)
- [Security group rules for common use cases](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/security-group-rules-reference.html)
- [Create a security group](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/working-with-security-groups.html)
