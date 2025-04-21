
### API
### normal request
```shell
curl -X POST \
  "http://localhost:9090/api/v1/convert/markdown/pdf" \
  -F "fileInput=@"/Users/carpewang/output.md";type=text/markdown" \
  --output output.pdf
```
* response
```shell
  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                 Dload  Upload   Total   Spent    Left  Speed
100  9430  100  8681  100   749  10669    920 --:--:-- --:--:-- --:--:-- 11599
```
### existed file
```shell
carpewang@wangkaipengdeMacBook-Pro ~ % curl -X POST "http://localhost:9090/api/v1/convert/markdown/pdf" \
  -H "Origin: http://localhost:9090" \
  -H "Referer: http://localhost:9090/markdown-to-pdf" \
  -F "fileInput=@\"/Users/carpewang/Desktop/Software Testing/Stirling-PDF/testResult/MarkdowntoPDF/Markdown.md\";type=text/markdown" \
  --output output.pdf
```
* response
```shell
  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                 Dload  Upload   Total   Spent    Left  Speed
100  8004  100  7556  100   448    228     13  0:00:34  0:00:32  0:00:02  2035
carpewang@wangkaipengdeMacBook-Pro ~ %
```
### Empty Markdown File
```curl
curl -X POST "http://localhost:9090/api/v1/convert/markdown/pdf" \
  -H "Origin: http://localhost:9090" \
  -H "Referer: http://localhost:9090/markdown-to-pdf" \
  -F "fileInput=@/dev/null;type=text/markdown"\
  --output output.pdf

```
response
```json
  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
Dload  Upload   Total   Spent    Left  Speed
100  1116  100   911  100   205   2107    474 --:--:-- --:--:-- --:--:--  2583
```
........
### Missing fileInput Field
```curl
curl -X POST "http://localhost:9090/api/v1/convert/markdown/pdf" \
  -H "Origin: http://localhost:9090" \
  -H "Referer: http://localhost:9090/markdown-to-pdf" \
  -H "Content-Type: multipart/form-data" \
  --data ""
```
response
```json
{"timestamp":"2025-04-19T18:59:30.166+00:00",
    "status":400,
    "error":"Bad Request",
    "exception":"org.springframework.web.multipart.MultipartException",
    "trace":"because trace is too large so ignored"
}
```

### Unsupported File Type
```shell
echo "Hello" > test.txt
```
```curl
curl -X POST "http://localhost:9090/api/v1/convert/markdown/pdf" \
  -H "Origin: http://localhost:9090" \
  -H "Referer: http://localhost:9090/markdown-to-pdf" \
  -F "fileInput=@/Users/carpewang/test.txt;type=text/plain"
```
response:
```json
{
    "timestamp":"2025-04-19T19:02:56.860+00:00",
    "status":500,
    "error":"Internal Server Error",
    "exception":"java.lang.IllegalArgumentException",
    "trace":"File must be in .md format. because trace is too large so ignored"
}
```
### Large Markdown File
```shell
yes "# Title" | head -n 1000000 > large_markdown.md
```
```curl
curl -X POST "http://localhost:9090/api/v1/convert/markdown/pdf" \
  -H "Origin: http://localhost:9090" \
  -H "Referer: http://localhost:9090/markdown-to-pdf" \
  -F "fileInput=@/Users/carpewang/large_markdown.md;type=text/markdown" \
  --output large_output.pdf
```
response
```shell
  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                 Dload  Upload   Total   Spent    Left  Speed
100 7819k    0  7464  100 7812k     70  75939  0:01:45  0:01:45 --:--:--  1599

```

