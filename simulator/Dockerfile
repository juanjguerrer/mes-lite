FROM python:3.12-slim

ENV PYTHONUNBUFFERED=1
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY config config
COPY plcsim plcsim
USER nobody
EXPOSE 4840
CMD ["python", "-m", "plcsim.server"]
