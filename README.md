# Add-On Synchronizer

WebCTRL is a trademark of Automated Logic Corporation.  Any other trademarks mentioned herein are the property of their respective owners.

- [Add-On Synchronizer](#add-on-synchronizer)
  - [Overview](#overview)
  - [Database Installation](#database-installation)
  - [Add-On Installation](#add-on-installation)
  - [Database Structure](#database-structure)

## Overview

Includes a database application that runs as a *Windows* service and a WebCTRL add-on that communicates with the database. The primary function is to synchronize add-ons across connected WebCTRL servers. The last modified timestamp for each add-on file is compared to determine whether existing add-ons should be updated. Add-ons automatically restart on each server when updates are required. This application is intended to be useful for *Automated Logic* dealer branches that maintain hundreds of WebCTRL servers on behalf of clients.

Before proceeding, there are some network requirements that must be met. The host computer for the database must be able to accept incoming connections from the public IP address of each WebCTRL server. Similarly, each WebCTRL server must be permitted to establish out-bound connections to the database computer's public IP address. Usually, out-bound connections are enabled by default, so the only required change may be to setup port-forwarding through the firewall on the database's network.

All network traffic between the database and each WebCTRL server is securely encrypted using a form of XOR stream cipher with symmetric keys established through RSA encryption. [Forward secrecy](https://en.wikipedia.org/wiki/Forward_secrecy) of each symmetric key is ensured.

## Database Installation

1. Identify a Windows machine you control with nearly 100% uptime. Choose a port to use for network communication (the default is 1978). Ensure port-forwarding is setup on your network firewall to allow inbound connections from all WebCTRL server IP addresses.

1. Download the latest release of the [database installation files](https://github.com/automatic-controls/addon-synchronizer/releases/latest/download/Database.zip).

1. Un-zip and place the installation files into an empty folder in a secure location on your Windows machine.

1. Run *./install.vbs* to install and start the database Windows service on your computer.

1. Run *./stop.vbs* or stop the database service using alternate means. At this point, the application should have generated files under the *./data* directory.

2. Make necessary changes to *./data/config.txt* and restart the database service. During this step, you may change the port to which the database binds. Note that all such manual modifications should take place while the database is inactive (or risk having your changes overwritten). You will want to record the values of *PublicKeyHash* and *ConnectionKey* from this configuration file, since they will be used for configuring the add-on component.

   | Property | Description |
   | - | - |
   | *Version* | Specifies the version of the application which generated the data files. On initialization, this value is used to determine compatibility. |
   | *Port* | Specifies the port bound to the database for listening to incoming connections. |
   | *PublicKeyHash* | Used to authenticate the database's identity to the client. This may be shared freely. |
   | *ConnectionKey* | Used to authenticate the client's identity to the database. This should be kept secret. |
   | *BackLog* | Specifies the maximum number of pending network connections for processing. All incoming connections will be rejected after this limit is surpassed. |
   | *Timeout* | Specifies how long to wait (in milliseconds) for connected WebCTRL servers to respond before assuming the connection has been lost. |
   | *DeleteLogAfter* | Specifies how long (in milliseconds) to keep historical log records. Logs are recorded in the file *./data/log.txt*. |

## Add-On Installation

1. Ensure your WebCTRL server machine can establish outbound connections to the database's IP address and port. Please ensure your server is protected with a TLS/SSL certificate (i.e. your server is accessible using the HTTPS protocol as opposed to HTTP).

1. If your WebCTRL server requires signed add-ons, download the [authenticating certificate](https://github.com/automatic-controls/addon-dev-script/blob/main/ACES.cer?raw=true) to the *./addons* directory of your server.

1. Download [AddonSynchronizer.addon](https://github.com/automatic-controls/addon-synchronizer/releases/latest/download/AddonSynchronizer.addon) and install it on your server using the WebCTRL interface.

1. Navigate to the add-on's main page using the WebCTRL interface.

2. Fill out all fields and press **Save**.

   | Property | Description |
   | - | - |
   | *Host* | Specifies the IP address of the database to connect to. |
   | *Port* | Specifies the port to use for communications to the database. |
   | *Connection Key* | Used to authenticate the add-on's identity. This value should be retrieved from the database configuration file. |
   | *Timeout* | Specifies how long to wait (in milliseconds) for the database to respond before assuming the connection has been lost. |
   | *Log Expiry* | Specifies how long to keep log entries (in milliseconds). |
   | *Sync Schedule* | Specifies a [Cron expression](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/support/CronExpression.html#parse(java.lang.String)) that determines when to query the database for add-on synchronization. If no expression is given, the database will be queried daily. |

1. Click the **Clear Public-Key** button to erase any cached keys.

3. Wait 10 minutes and verify that a connection between your WebCTRL server and database has been successfully established. Or you could disable / reenable the addon to check the connection more quickly. After connecting, the status message should say: *Success*.

4. Verify the *PublicKeyHash* from the database matches.

## Database Structure

This section describes possible files and folders stored under the *./data* directory of the database installation folder. Note that all manual changes to *./config.txt* and *./keys* should be made while the database is inactive (or risk having your changes overwritten).

| File | Description |
| - | - |
| *./addons* | Folder containing *.addon* files to synchronize. |
| *./config.txt* | File containing configuration properties for the database. |
| *./log.txt* | File containing historical log entries for the database. |
| *./keys* | File containing public and private RSA keys used for the initial handshake protocol when establishing a secure connection to WebCTRL servers. |
| *./lock* | File used to ensure there are never two instances of the database running at the same time. |