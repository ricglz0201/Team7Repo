+<%--
  Copyright 2017 Google Inc.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
--%>
<!DOCTYPE html>
<html>
<head>
    <title>Profile Page</title>
    <link rel="stylesheet" href="/css/main.css">
</head>
<body>

<nav>
    <a id="navTitle" href="/">CodeU Chat App</a>
    <a href="/conversations">Conversations</a>
    <% if (request.getSession().getAttribute("user") != null) { %>
    <a>Hello <%= request.getSession().getAttribute("user") %>!</a>
    <% } else { %>
    <a href="/login">Login</a>
    <% } %>
    <a href="/about.jsp">About</a>
    <%@ include file="navbar.jsp" %>
</nav>

<div id="container">
    <% if (request.getAttribute("error") != null) { %>
    <h2 style="color:red"><%= request.getAttribute("error") %>
    </h2>
    <% } %>

    <h2><a>My Profile Page </a></h2>
    <br>
    <br>
    <!--About Section -->
    <h3>About You!</h3>
    <!-- info from descriptions -->
    <%=request.getSession().getAttribute("description")%>
    <br>

    <h3> Edit your About Me (only you can see this)</h3>
    <form action="/profile" method="POST">
        <label for="description">Description</label>
        <input type="text" name="description" id="description">
        <button style="height:20px;width:20px" button type = submit>Submit</button>
    </form>
    <hr>

</div>
</body>
</html>