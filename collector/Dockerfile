# syntax=docker/dockerfile:1

FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /build
COPY src/MesLite.Collector/MesLite.Collector.csproj src/MesLite.Collector/
RUN dotnet restore src/MesLite.Collector/MesLite.Collector.csproj
COPY src src
RUN dotnet publish src/MesLite.Collector/MesLite.Collector.csproj -c Release -o /app --no-restore

FROM mcr.microsoft.com/dotnet/runtime:10.0
WORKDIR /app
# The outbox lives on a volume so unsent events survive a container restart.
RUN mkdir /data && chown $APP_UID /data
USER $APP_UID
COPY --from=build /app .
ENV Collector__OutboxPath=/data/outbox.db
ENTRYPOINT ["dotnet", "MesLite.Collector.dll"]
