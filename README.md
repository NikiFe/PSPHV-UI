# Guide for hosting this locally
## 1. clone this repo
**clone this repo** using 
```
git clone https://github.com/NikiFe/PSPHV-UI.git
```
to some place that is **accessible** and somewhere **where you will find it**
## 2. install mongodb
just **install [mongodb](https://www.mongodb.com/try/download/community)**, its shrimple 🦐 as that
### 2.1 set up mongodb (using [mongodb compass](https://www.mongodb.com/products/tools/compass))
install **mongodb compass** to have some sanity left when you finish this, **create a new connection** to the **localhost** running mongodb. Create a **new database** called **parliament**, with the **first collection named users**. **If you have a backup then import it now**, if you don't, just continue.
## 3. run the app itself
using the IDE of your choice (personally would recommend [intellij idea](https://www.jetbrains.com/idea/)) **install all the required maven dependancies and run the app**. If you get any error in this moment, *tough luck*. Now you should be able to **access the app on localhost:8080**.
To **create your first user** (**if** you **didn't import** them previously) **register in the form on the site**. You should **have some user be the president at all times** (or its completely pointless), so go **check your mongodb compass in the users collection**(always **ctrl+r** when you are about to look at anything in mongodb, just a hint). You should see your newly created user there, **change their "role" field to "PRESIDENT"**. Now **restart the app** for permissions to get updated. You should be able to now **log in** and be able to enter new proposals. 
Also at this step **when you have your president user**, you can easily **access the AdminUI™** on [localhost:8080/admin.html](localhost:8080/admin.html), there you will be able to **manage all of the users** and easily modify everything about them.
## 4. set up discord webhook
in your **system variables** set a variable named **"DISCORD_WEBHOOK_URL"** and set **its value to your discord webhook**. Congrats! You just successfully made the discord implementation work.
## 5. open it to the world
for this to be accessible to the outside world, you need to **either port forward and open your firewall to port 8080**, or **use no-ip so your ip is not visible when sharing the website**. You should be able to **find** enough **sources** on how to do this anywhere else online, that i will not explain it, as its quite trivial
## 6. enjoy the chaos
thats about it, you should have a **fully functioning version of the PSPHVUI app**
# enjoy
